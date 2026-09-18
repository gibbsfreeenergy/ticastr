#!/usr/bin/env python3
"""Signed bridge for the local xray-dash SQLite database and safe controls.

The bridge does not reuse xray-dash's browser session. Read requests expose
aggregates, while write requests are limited to the explicitly allowlisted
operations below and require an HMAC signature that also covers the body.
"""

from __future__ import annotations

import hashlib
import hmac
import ipaddress
import json
import logging
import os
import sqlite3
import subprocess
import threading
import time
from collections import defaultdict
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable
from urllib.parse import parse_qs, urlsplit


LOG = logging.getLogger("xray-traffic-bridge")
TZ_OFFSET = 8 * 60 * 60
DEFAULT_DB_PATH = "/opt/xray-dash/data/traffic.db"
DEFAULT_STATE_PATH = "/opt/xray-dash/data/state.json"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8788
MAX_CLOCK_SKEW = 90
MAX_LIMIT = 500
MAX_BODY_BYTES = 64 * 1024
MAX_REASON_LENGTH = 200
MAX_LABEL_LENGTH = 80
MAX_RULE_ID_LENGTH = 64
MAX_RULE_EMAIL_LENGTH = 200
BLOCK_RULE_TAG = "sourceIpBlock"
DEFAULT_XRAY_BIN = "/usr/local/bin/xray"
DEFAULT_XRAY_API = "127.0.0.1:10085"
DEFAULT_XRAY_INBOUND = "proxy-in"
DEFAULT_BLOCK_OUTBOUND = "block"
DEFAULT_XRAY_DASH_HOME = "/opt/xray-dash"
DEFAULT_XRAY_DASH_ENV_FILE = "/etc/xray-dash/environment"
DEFAULT_XRAY_DASH_COLLECTOR = "/opt/xray-dash/xray_dash.py"
DEFAULT_GEO_PATH = "/opt/xray-dash/data/geoip.db"


def now_ts() -> int:
    return int(time.time())


def hour_key(ts: int) -> int:
    return (int(ts) + TZ_OFFSET) // 3600


def day_key(ts: int) -> int:
    return (int(ts) + TZ_OFFSET) // 86400


def format_time(ts: int | None, with_date: bool = True) -> str:
    if not ts:
        return ""
    value = datetime.fromtimestamp(int(ts) + TZ_OFFSET, tz=timezone.utc)
    return value.strftime("%Y-%m-%d %H:%M:%S" if with_date else "%H:%M:%S")


def json_response(handler: BaseHTTPRequestHandler, status: int, payload: Any) -> None:
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Cache-Control", "no-store")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def clamp_int(value: str | None, default: int, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value or default)
    except (TypeError, ValueError):
        return default
    return max(minimum, min(maximum, parsed))


def is_non_local(ip: str | None) -> bool:
    if not ip:
        return False
    try:
        value = ipaddress.ip_address(ip)
        return not (value.is_loopback or value.is_private or value.is_link_local)
    except ValueError:
        return False


def validate_ip(value: Any, *, ipv4_only: bool = False) -> str:
    ip = str(value or "").strip()
    try:
        parsed = ipaddress.ip_address(ip)
    except ValueError as error:
        raise ValueError("IP 地址格式不正确") from error
    if not is_non_local(ip):
        raise ValueError("不允许操作本机或内网 IP")
    if ipv4_only and parsed.version != 4:
        raise ValueError("黑名单目前只支持公网 IPv4 地址")
    return ip


def clean_text(value: Any, field: str, maximum: int, *, required: bool = False) -> str:
    text = str(value or "").strip()
    if required and not text:
        raise ValueError(f"{field}不能为空")
    if len(text) > maximum:
        raise ValueError(f"{field}过长")
    if any(character in text for character in ("\r", "\n")):
        raise ValueError(f"{field}不能包含换行符")
    return text


class TrafficStore:
    def __init__(
        self,
        db_path: str = DEFAULT_DB_PATH,
        state_path: str = DEFAULT_STATE_PATH,
        clock: Callable[[], int] = now_ts,
        xray_status: Callable[[], str] | None = None,
        xray_bin: str = DEFAULT_XRAY_BIN,
        xray_api: str = DEFAULT_XRAY_API,
        xray_inbound: str = DEFAULT_XRAY_INBOUND,
        block_outbound: str = DEFAULT_BLOCK_OUTBOUND,
        collector_bin: str = "/usr/bin/python3",
        collector_home: str = DEFAULT_XRAY_DASH_HOME,
        collector_script: str = DEFAULT_XRAY_DASH_COLLECTOR,
        geo_path: str = DEFAULT_GEO_PATH,
        collector_env_file: str = DEFAULT_XRAY_DASH_ENV_FILE,
        command_runner: Callable[[list[str], int], tuple[int, str, str]] | None = None,
    ) -> None:
        self.db_path = db_path
        self.state_path = state_path
        self.clock = clock
        self.xray_status = xray_status or read_xray_status
        self.xray_bin = xray_bin
        self.xray_api = xray_api
        self.xray_inbound = xray_inbound
        self.block_outbound = block_outbound
        self.collector_bin = collector_bin
        self.collector_home = collector_home
        self.collector_script = collector_script
        self.geo_path = geo_path
        self.collector_env_file = collector_env_file
        self.command_runner = command_runner or self._run_command
        self.control_lock = threading.Lock()

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(
            f"file:{self.db_path}?mode=ro", uri=True, timeout=10
        )
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout=10000")
        return connection

    def connect_rw(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path, timeout=10)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout=10000")
        return connection

    def _collector_environment(self) -> dict[str, str]:
        environment = os.environ.copy()
        try:
            with open(self.collector_env_file, "r", encoding="utf-8") as stream:
                for line in stream:
                    line = line.strip()
                    if not line or line.startswith("#") or "=" not in line:
                        continue
                    key, value = line.split("=", 1)
                    key = key.strip()
                    value = value.strip()
                    if key.startswith("XRAY_DASH_"):
                        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
                            value = value[1:-1]
                        environment[key] = value
        except OSError:
            LOG.warning("xray-dash environment file is unavailable: %s", self.collector_env_file)
        environment.setdefault("XRAY_DASH_HOME", self.collector_home)
        environment.setdefault("XRAY_DASH_DB", self.db_path)
        environment.setdefault("XRAY_DASH_STATE", self.state_path)
        environment.setdefault("XRAY_DASH_GEO", self.geo_path)
        return environment

    def _run_command(self, command: list[str], timeout: int) -> tuple[int, str, str]:
        try:
            result = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
                timeout=timeout,
                env=self._collector_environment() if command and command[0] == self.collector_bin else None,
            )
            return result.returncode, result.stdout.strip(), result.stderr.strip()
        except (OSError, subprocess.SubprocessError) as error:
            return 1, "", str(error)

    def _settings(self, connection: sqlite3.Connection) -> dict[str, str]:
        try:
            rows = connection.execute("SELECT k, v FROM settings").fetchall()
        except sqlite3.Error:
            return {}
        return {str(row["k"]): str(row["v"] or "") for row in rows}

    def _state(self) -> dict[str, Any]:
        try:
            with open(self.state_path, "r", encoding="utf-8") as stream:
                value = json.load(stream)
            return value if isinstance(value, dict) else {}
        except (OSError, ValueError, TypeError):
            return {}

    @staticmethod
    def _timestamp(value: Any) -> int | None:
        try:
            timestamp = int(value)
        except (TypeError, ValueError):
            return None
        return timestamp if timestamp > 0 else None

    def overview(self) -> dict[str, Any]:
        with self.connect() as connection:
            settings = self._settings(connection)
            hide_local = settings.get("exclude_local", "1") == "1"
            local_clause = "AND c.src NOT IN (SELECT ip FROM ip_meta WHERE cc='LO')" if hide_local else ""
            today_start = day_key(self.clock()) * 86400 - TZ_OFFSET
            total_connections = connection.execute("SELECT COUNT(*) FROM conns").fetchone()[0]
            total_ips = connection.execute(
                "SELECT COUNT(*) FROM ip_meta WHERE cc IS NOT NULL AND cc<>'LO'"
            ).fetchone()[0]
            today_connections = connection.execute(
                f"SELECT COUNT(*) FROM conns c WHERE c.ts>=? {local_clause}",
                (today_start,),
            ).fetchone()[0]
            total_domains = connection.execute(
                "SELECT COUNT(DISTINCT host) FROM conns WHERE host IS NOT NULL AND host<>''"
            ).fetchone()[0]
            first_connection = connection.execute("SELECT MIN(ts) FROM conns").fetchone()[0]
            last_connection = connection.execute("SELECT MAX(ts) FROM conns").fetchone()[0]
            open_alerts = connection.execute(
                "SELECT COUNT(*) FROM alerts WHERE acked=0"
            ).fetchone()[0]
            traffic = connection.execute(
                "SELECT ts, up, down, online FROM traffic ORDER BY ts DESC LIMIT 1"
            ).fetchone()

        # Recent Rust versions of xray-dash keep the traffic samples current but
        # no longer refresh the legacy state.json heartbeat. Prefer the database
        # sample timestamp and only fall back to state.json for older installs.
        traffic_at = self._timestamp(traffic["ts"] if traffic else None)
        state = self._state()
        state_at = self._timestamp(state.get("at"))
        collector_at = traffic_at or state_at
        collector_lag = self.clock() - collector_at if collector_at else None
        return {
            "total_conns": int(total_connections or 0),
            "total_ips": int(total_ips or 0),
            "today_conns": int(today_connections or 0),
            "total_domains": int(total_domains or 0),
            "first_conn": format_time(first_connection),
            "last_conn": format_time(last_connection),
            "alerts_open": int(open_alerts or 0),
            "traffic_up": int(traffic["up"] if traffic else 0),
            "traffic_down": int(traffic["down"] if traffic else 0),
            "online": int(traffic["online"] if traffic else 0),
            "xray": self.xray_status(),
            "collector": {
                "at": int(collector_at) if collector_at else None,
                "lag": collector_lag,
            },
            "timezone": "Asia/Shanghai",
            "generated_at": self.clock(),
        }

    def timeseries(self, hours: int = 48) -> list[dict[str, Any]]:
        end_hour = hour_key(self.clock())
        start_hour = end_hour - hours + 1
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT hour, SUM(n) AS n, COUNT(*) AS ips FROM agg_hour "
                "WHERE hour>=? GROUP BY hour ORDER BY hour",
                (start_hour,),
            ).fetchall()
            traffic_rows = connection.execute(
                "SELECT ts, up, down FROM traffic WHERE ts>=? ORDER BY ts",
                ((start_hour * 3600) - TZ_OFFSET - 3600,),
            ).fetchall()

        aggregates = {int(row["hour"]): (int(row["n"]), int(row["ips"])) for row in rows}
        upload_by_hour: defaultdict[int, int] = defaultdict(int)
        download_by_hour: defaultdict[int, int] = defaultdict(int)
        previous: tuple[int, int] | None = None
        for row in traffic_rows:
            current = (int(row["up"] or 0), int(row["down"] or 0))
            if previous is not None:
                upload_delta = current[0] - previous[0]
                download_delta = current[1] - previous[1]
                bucket = hour_key(int(row["ts"]))
                if bucket >= start_hour:
                    if upload_delta >= 0:
                        upload_by_hour[bucket] += upload_delta
                    if download_delta >= 0:
                        download_by_hour[bucket] += download_delta
            previous = current

        output = []
        for bucket in range(start_hour, end_hour + 1):
            connections, unique_ips = aggregates.get(bucket, (0, 0))
            label = format_time(bucket * 3600 - TZ_OFFSET).rsplit(":", 1)[0]
            output.append(
                {
                    "bucket": bucket,
                    "label": label,
                    "conns": connections,
                    "ips": unique_ips,
                    "up": upload_by_hour.get(bucket, 0),
                    "down": download_by_hour.get(bucket, 0),
                }
            )
        return output

    def daily(self, days: int = 30) -> list[dict[str, Any]]:
        end_day = day_key(self.clock())
        start_day = end_day - days + 1
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT hour/24 AS day, SUM(n) AS n, COUNT(*) AS ips FROM agg_hour "
                "WHERE hour>=? GROUP BY day ORDER BY day",
                (start_day * 24,),
            ).fetchall()
        aggregates = {int(row["day"]): (int(row["n"]), int(row["ips"])) for row in rows}
        return [
            {
                "label": datetime.fromtimestamp(day * 86400, tz=timezone.utc).strftime("%m-%d"),
                "conns": aggregates.get(day, (0, 0))[0],
                "ips": aggregates.get(day, (0, 0))[1],
            }
            for day in range(start_day, end_day + 1)
        ]

    def sources(
        self,
        days: int = 30,
        limit: int = 12,
        search: str = "",
        only_foreign: bool = False,
    ) -> dict[str, Any]:
        since = self.clock() - days * 86400
        settings_search = search.strip()[:80]
        where = ["c.ts>=?"]
        args: list[Any] = [since]
        with self.connect() as connection:
            settings = self._settings(connection)
            if settings.get("exclude_local", "1") == "1":
                where.append("c.src NOT IN (SELECT ip FROM ip_meta WHERE cc='LO')")
            if settings_search:
                where.append("(c.src LIKE ? OR m.org LIKE ? OR m.prov LIKE ? OR m.city LIKE ?)")
                args.extend([f"%{settings_search}%"] * 4)
            if only_foreign:
                where.append("(m.cc IS NULL OR m.cc<>'CN')")
            clause = " AND ".join(where)
            rows = connection.execute(
                f"SELECT c.src, COUNT(*) AS n, MIN(c.ts) AS first_ts, MAX(c.ts) AS last_ts, "
                f"COUNT(DISTINCT c.host) AS hosts, m.cc, m.prov, m.city, m.org, m.label, m.blocked "
                f"FROM conns c LEFT JOIN ip_meta m ON m.ip=c.src WHERE {clause} "
                "GROUP BY c.src ORDER BY n DESC LIMIT ?",
                (*args, limit),
            ).fetchall()
            total = connection.execute(
                f"SELECT COUNT(DISTINCT c.src) FROM conns c LEFT JOIN ip_meta m ON m.ip=c.src WHERE {clause}",
                args,
            ).fetchone()[0]
        return {
            "total": int(total or 0),
            "items": [
                {
                    "src": row["src"],
                    "n": int(row["n"] or 0),
                    "hosts": int(row["hosts"] or 0),
                    "cc": row["cc"],
                    "prov": row["prov"],
                    "place": " ".join(value for value in (row["prov"], row["city"]) if value),
                    "org": row["org"],
                    "label": row["label"],
                    "blocked": int(row["blocked"] or 0),
                    "first": format_time(row["first_ts"]),
                    "last": format_time(row["last_ts"]),
                }
                for row in rows
            ],
        }

    def targets(self, days: int = 30, limit: int = 12) -> list[dict[str, Any]]:
        since = self.clock() - days * 86400
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT host, COUNT(*) AS n, COUNT(DISTINCT src) AS ips, MIN(ts) AS first_ts, "
                "MAX(ts) AS last_ts FROM conns WHERE ts>=? AND host IS NOT NULL AND host<>'' "
                "GROUP BY host ORDER BY n DESC LIMIT ?",
                (since, limit),
            ).fetchall()
        return [
            {
                "host": row["host"],
                "n": int(row["n"] or 0),
                "ips": int(row["ips"] or 0),
                "first": format_time(row["first_ts"]),
                "last": format_time(row["last_ts"]),
            }
            for row in rows
        ]

    def geo(self, days: int = 30) -> dict[str, Any]:
        since = self.clock() - days * 86400
        with self.connect() as connection:
            countries = connection.execute(
                "SELECT COALESCE(m.cc,'??') AS cc, COUNT(*) AS n, COUNT(DISTINCT c.src) AS ips "
                "FROM conns c LEFT JOIN ip_meta m ON m.ip=c.src WHERE c.ts>=? "
                "AND (m.cc IS NULL OR m.cc<>'LO') GROUP BY cc ORDER BY n DESC",
                (since,),
            ).fetchall()
            provinces = connection.execute(
                "SELECT COALESCE(m.prov,'未知') AS prov, COUNT(*) AS n, COUNT(DISTINCT c.src) AS ips "
                "FROM conns c LEFT JOIN ip_meta m ON m.ip=c.src WHERE c.ts>=? AND m.cc='CN' "
                "GROUP BY prov ORDER BY n DESC LIMIT 15",
                (since,),
            ).fetchall()
            organizations = connection.execute(
                "SELECT COALESCE(m.org,'未知') AS org, COUNT(*) AS n FROM conns c "
                "LEFT JOIN ip_meta m ON m.ip=c.src WHERE c.ts>=? AND (m.cc IS NULL OR m.cc<>'LO') "
                "GROUP BY org ORDER BY n DESC LIMIT 15",
                (since,),
            ).fetchall()
        return {
            "countries": [{"cc": row["cc"], "n": int(row["n"]), "ips": int(row["ips"])} for row in countries],
            "provinces": [{"prov": row["prov"], "n": int(row["n"]), "ips": int(row["ips"])} for row in provinces],
            "orgs": [{"org": row["org"], "n": int(row["n"]) } for row in organizations],
        }

    def live(self, limit: int = 150) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT c.ts, c.src, c.net, c.host, c.port FROM conns c "
                "WHERE c.src NOT IN (SELECT ip FROM ip_meta WHERE cc='LO') "
                "ORDER BY c.ts DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [
            {
                "t": format_time(row["ts"], with_date=False),
                "ts": int(row["ts"]),
                "src": row["src"],
                "net": row["net"],
                "host": row["host"],
                "port": row["port"],
            }
            for row in rows
        ]

    def alerts(self, limit: int = 100) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT id, ts, level, kind, title, detail, acked FROM alerts ORDER BY ts DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [
            {
                "id": int(row["id"]),
                "t": format_time(row["ts"]),
                "level": row["level"],
                "kind": row["kind"],
                "title": row["title"],
                "detail": row["detail"],
                "acked": int(row["acked"] or 0),
            }
            for row in rows
        ]

    def blocklist(self) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT b.ip, b.reason, b.active, b.created_at, m.cc, m.prov, m.city, m.org "
                "FROM blocklist b LEFT JOIN ip_meta m ON m.ip=b.ip ORDER BY b.created_at DESC"
            ).fetchall()
        return [
            {
                "ip": row["ip"],
                "reason": row["reason"] or "",
                "active": int(row["active"] or 0),
                "created": format_time(row["created_at"]),
                "place": " ".join(value for value in (row["prov"], row["city"]) if value),
                "org": row["org"],
                "cc": row["cc"],
            }
            for row in rows
        ]

    def alert_rules(self) -> list[dict[str, Any]]:
        with self.connect() as connection:
            rows = connection.execute(
                "SELECT id, enabled, metric, threshold, window_sec, cooldown_sec, level, email "
                "FROM alert_rules ORDER BY id"
            ).fetchall()
        return [
            {
                "id": row["id"],
                "enabled": int(row["enabled"] or 0),
                "metric": row["metric"] or "",
                "threshold": int(row["threshold"] or 0),
                "window_sec": int(row["window_sec"] or 0),
                "cooldown_sec": int(row["cooldown_sec"] or 0),
                "level": row["level"] or "info",
                "email": row["email"] or "",
            }
            for row in rows
        ]

    def _sync_blocklist_locked(self, connection: sqlite3.Connection) -> dict[str, Any]:
        ips = [row[0] for row in connection.execute(
            "SELECT ip FROM blocklist WHERE active=1 ORDER BY created_at DESC"
        ).fetchall()]
        if ips:
            command = [
                self.xray_bin,
                "api",
                "sib",
                f"--server={self.xray_api}",
                f"-inbound={self.xray_inbound}",
                f"-outbound={self.block_outbound}",
                "-reset",
                *ips,
            ]
        else:
            command = [
                self.xray_bin,
                "api",
                "rmrules",
                f"--server={self.xray_api}",
                BLOCK_RULE_TAG,
            ]
        returncode, stdout, stderr = self.command_runner(command, 15)
        if returncode != 0:
            LOG.warning("xray blocklist sync failed: rc=%s stderr=%s", returncode, stderr[-500:])
        return {
            "ok": returncode == 0,
            "mode": "blocklist-sync",
            "runtime_applied": returncode == 0,
            "active": ips,
            "error": "Xray 黑名单同步失败" if returncode != 0 else "",
        }

    def block_ip(self, ip: Any, reason: Any = "") -> dict[str, Any]:
        normalized_ip = validate_ip(ip, ipv4_only=True)
        normalized_reason = clean_text(reason, "封禁原因", MAX_REASON_LENGTH)
        with self.control_lock, self.connect_rw() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO blocklist(ip, reason, created_at, active) VALUES(?,?,?,1)",
                (normalized_ip, normalized_reason, self.clock()),
            )
            connection.execute("UPDATE ip_meta SET blocked=1 WHERE ip=?", (normalized_ip,))
            connection.commit()
            result = self._sync_blocklist_locked(connection)
        return {"ip": normalized_ip, "reason": normalized_reason, **result}

    def unblock_ip(self, ip: Any) -> dict[str, Any]:
        normalized_ip = validate_ip(ip, ipv4_only=True)
        with self.control_lock, self.connect_rw() as connection:
            connection.execute("UPDATE blocklist SET active=0 WHERE ip=?", (normalized_ip,))
            connection.execute("UPDATE ip_meta SET blocked=0 WHERE ip=?", (normalized_ip,))
            connection.commit()
            result = self._sync_blocklist_locked(connection)
        return {"ip": normalized_ip, **result}

    def label_ip(self, ip: Any, label: Any = "") -> dict[str, Any]:
        normalized_ip = validate_ip(ip)
        normalized_label = clean_text(label, "IP 备注", MAX_LABEL_LENGTH)
        with self.control_lock, self.connect_rw() as connection:
            connection.execute(
                "INSERT OR IGNORE INTO ip_meta(ip, cc, prov, city, org, first_seen, last_seen, n, label, blocked) "
                "VALUES(?,?,?,?,?,?,?,?,?,0)",
                (normalized_ip, "", "", "", "", 0, 0, 0, ""),
            )
            connection.execute("UPDATE ip_meta SET label=? WHERE ip=?", (normalized_label, normalized_ip))
            connection.commit()
        return {
            "ok": True,
            "mode": "label",
            "runtime_applied": False,
            "ip": normalized_ip,
            "label": normalized_label,
        }

    def acknowledge_alert(self, alert_id: Any = None, acknowledge_all: Any = False) -> dict[str, Any]:
        if not acknowledge_all:
            try:
                normalized_id = int(alert_id)
            except (TypeError, ValueError) as error:
                raise ValueError("告警 ID 不正确") from error
            if normalized_id <= 0:
                raise ValueError("告警 ID 不正确")
        with self.control_lock, self.connect_rw() as connection:
            if acknowledge_all:
                cursor = connection.execute("UPDATE alerts SET acked=1 WHERE acked=0")
            else:
                cursor = connection.execute("UPDATE alerts SET acked=1 WHERE id=?", (normalized_id,))
            connection.commit()
        return {
            "ok": True,
            "mode": "ack",
            "runtime_applied": False,
            "updated": int(cursor.rowcount or 0),
        }

    def save_alert_rule(self, payload: dict[str, Any]) -> dict[str, Any]:
        rule_id = clean_text(payload.get("id"), "规则 ID", MAX_RULE_ID_LENGTH, required=True)
        if not all(character.isalnum() or character in "._:-" for character in rule_id):
            raise ValueError("规则 ID 只能包含字母、数字、点、下划线、冒号和短横线")
        metric = clean_text(payload.get("metric"), "监控指标", 64, required=True)
        if not all(character.isalnum() or character in "._:-" for character in metric):
            raise ValueError("监控指标格式不正确")
        level = clean_text(payload.get("level") or "info", "告警级别", 16, required=True).lower()
        if level not in {"info", "medium", "high", "critical", "warn"}:
            raise ValueError("告警级别不支持")
        email = clean_text(payload.get("email"), "通知目标", MAX_RULE_EMAIL_LENGTH)
        try:
            threshold = int(payload.get("threshold"))
            window_sec = int(payload.get("window_sec"))
            cooldown_sec = int(payload.get("cooldown_sec"))
        except (TypeError, ValueError) as error:
            raise ValueError("告警阈值和时间必须是整数") from error
        if not 0 <= threshold <= 1_000_000:
            raise ValueError("告警阈值超出范围")
        if not 1 <= window_sec <= 604800 or not 1 <= cooldown_sec <= 604800:
            raise ValueError("告警时间范围不正确")
        enabled = 1 if bool(payload.get("enabled", True)) else 0
        with self.control_lock, self.connect_rw() as connection:
            connection.execute(
                "INSERT INTO alert_rules(id, enabled, metric, threshold, window_sec, cooldown_sec, level, email) "
                "VALUES(?,?,?,?,?,?,?,?) "
                "ON CONFLICT(id) DO UPDATE SET enabled=excluded.enabled, metric=excluded.metric, "
                "threshold=excluded.threshold, window_sec=excluded.window_sec, "
                "cooldown_sec=excluded.cooldown_sec, level=excluded.level, email=excluded.email",
                (rule_id, enabled, metric, threshold, window_sec, cooldown_sec, level, email),
            )
            connection.commit()
        return {
            "ok": True,
            "mode": "alert-rule",
            "runtime_applied": False,
            "rule": {
                "id": rule_id,
                "enabled": enabled,
                "metric": metric,
                "threshold": threshold,
                "window_sec": window_sec,
                "cooldown_sec": cooldown_sec,
                "level": level,
                "email": email,
            },
        }

    def delete_alert_rule(self, rule_id: Any) -> dict[str, Any]:
        normalized_id = clean_text(rule_id, "规则 ID", MAX_RULE_ID_LENGTH, required=True)
        if not all(character.isalnum() or character in "._:-" for character in normalized_id):
            raise ValueError("规则 ID 格式不正确")
        with self.control_lock, self.connect_rw() as connection:
            cursor = connection.execute("DELETE FROM alert_rules WHERE id=?", (normalized_id,))
            connection.commit()
        return {
            "ok": True,
            "mode": "alert-rule-delete",
            "runtime_applied": False,
            "id": normalized_id,
            "deleted": int(cursor.rowcount or 0),
        }

    def sync_blocklist(self) -> dict[str, Any]:
        with self.control_lock, self.connect_rw() as connection:
            return self._sync_blocklist_locked(connection)

    def _run_collector(self, mode: str) -> dict[str, Any]:
        if mode != "collect":
            raise ValueError("采集操作不支持")
        # The installed legacy collector is deliberately used for a one-shot
        # run: the Rust dashboard process owns a database lock and cannot be
        # started a second time against the same SQLite file. The legacy
        # collector shares the same schema/state file and is idempotent via
        # the unique connection index.
        command = [self.collector_bin, self.collector_script, "--once"]
        timeout = 120
        returncode, stdout, stderr = self.command_runner(command, timeout)
        if returncode != 0:
            LOG.warning("xray-dash %s failed: rc=%s stderr=%s", mode, returncode, stderr[-500:])
        return {
            "ok": returncode == 0,
            "mode": mode,
            "runtime_applied": returncode == 0,
            "error": "采集器操作失败" if returncode != 0 else "",
            "output": stdout[-500:] if returncode == 0 else "",
        }

    def collect(self) -> dict[str, Any]:
        with self.control_lock:
            return self._run_collector("collect")

    def refresh_geo(self) -> dict[str, Any]:
        with self.control_lock:
            try:
                geo_connection = sqlite3.connect(self.geo_path, timeout=10)
                geo_connection.row_factory = sqlite3.Row
            except sqlite3.Error as error:
                LOG.warning("geo database is unavailable: %s", error)
                return {"ok": False, "mode": "geo", "runtime_applied": False, "error": "GeoIP 数据库不可用"}
            updated = 0
            try:
                with self.connect_rw() as connection:
                    rows = connection.execute(
                        "SELECT ip FROM ip_meta WHERE cc IS NULL OR cc='' OR prov IS NULL OR prov='' "
                        "OR city IS NULL OR city='' OR org IS NULL OR org='' LIMIT 20000"
                    ).fetchall()
                    for row in rows:
                        ip = row["ip"]
                        try:
                            parsed = ipaddress.ip_address(ip)
                        except ValueError:
                            continue
                        if parsed.is_loopback or parsed.is_private or parsed.is_link_local:
                            values = ("LO", "本机/内网", "", "")
                        elif parsed.version != 4:
                            continue
                        else:
                            number = int(parsed)
                            country = geo_connection.execute(
                                "SELECT cc, e FROM country WHERE s<=? ORDER BY s DESC LIMIT 1", (number,)
                            ).fetchone()
                            city = geo_connection.execute(
                                "SELECT prov, ct, e FROM city WHERE s<=? ORDER BY s DESC LIMIT 1", (number,)
                            ).fetchone()
                            organization = geo_connection.execute(
                                "SELECT org, e FROM asn WHERE s<=? ORDER BY s DESC LIMIT 1", (number,)
                            ).fetchone()
                            values = (
                                country["cc"] if country and country["e"] is not None and country["e"] >= number else None,
                                city["prov"] if city and city["e"] is not None and city["e"] >= number else None,
                                city["ct"] if city and city["e"] is not None and city["e"] >= number else None,
                                organization["org"] if organization and organization["e"] is not None and organization["e"] >= number else None,
                            )
                            if not any(values):
                                continue
                        connection.execute(
                            "UPDATE ip_meta SET cc=?, prov=?, city=?, org=? WHERE ip=?",
                            (*values, ip),
                        )
                        updated += 1
                    connection.commit()
            except sqlite3.Error as error:
                LOG.warning("geo refresh failed: %s", error)
                return {"ok": False, "mode": "geo", "runtime_applied": False, "error": "GeoIP 刷新失败"}
            finally:
                geo_connection.close()
        return {"ok": True, "mode": "geo", "runtime_applied": True, "updated": updated}

    def health(self) -> dict[str, Any]:
        with self.connect() as connection:
            connection.execute("SELECT 1").fetchone()
            last_connection = connection.execute("SELECT MAX(ts) FROM conns").fetchone()[0]
        return {
            "ok": True,
            "db": "ok",
            "last_connection": format_time(last_connection),
            "generated_at": self.clock(),
        }


def read_xray_status() -> str:
    try:
        result = subprocess.run(
            ["systemctl", "is-active", "xray"],
            check=False,
            capture_output=True,
            text=True,
            timeout=2,
        )
        return result.stdout.strip() or "unknown"
    except (OSError, subprocess.SubprocessError):
        return "unknown"


class BridgeHandler(BaseHTTPRequestHandler):
    server_version = "xray-traffic-bridge/1.0"

    def log_message(self, format_string: str, *args: Any) -> None:
        LOG.info("%s - %s", self.address_string(), format_string % args)

    @property
    def bridge_server(self) -> "BridgeServer":
        return self.server  # type: ignore[return-value]

    def _authorized(self, body: bytes = b"") -> bool:
        timestamp = self.headers.get("X-Ticastr-Traffic-Timestamp", "")
        signature = self.headers.get("X-Ticastr-Traffic-Signature", "")
        try:
            parsed_timestamp = int(timestamp)
        except ValueError:
            return False
        if abs(self.bridge_server.clock() - parsed_timestamp) > self.bridge_server.max_skew:
            return False
        canonical_text = f"{self.command}\n{self.path}\n{timestamp}"
        if self.command == "POST":
            body_digest = self.headers.get("X-Ticastr-Traffic-Body-SHA256", "").lower()
            expected_body_digest = hashlib.sha256(body).hexdigest()
            if not hmac.compare_digest(expected_body_digest, body_digest):
                return False
            canonical_text += f"\n{body_digest}"
        canonical = canonical_text.encode("utf-8")
        expected = hmac.new(
            self.bridge_server.secret.encode("utf-8"), canonical, hashlib.sha256
        ).hexdigest()
        return hmac.compare_digest(expected, signature)

    def do_GET(self) -> None:  # noqa: N802
        if not self._authorized():
            json_response(self, 401, {"error": "unauthorized"})
            return
        try:
            path = self._route_path()
            params = parse_qs(urlsplit(self.path).query)
            if path == "/v1/health":
                payload = self.bridge_server.store.health()
            elif path == "/v1/overview":
                payload = self.bridge_server.store.overview()
            elif path == "/v1/timeseries":
                hours = clamp_int(params.get("hours", [None])[0], 48, 1, 720)
                payload = self.bridge_server.store.timeseries(hours)
            elif path == "/v1/daily":
                days = clamp_int(params.get("days", [None])[0], 30, 1, 90)
                payload = self.bridge_server.store.daily(days)
            elif path == "/v1/sources":
                days = clamp_int(params.get("days", [None])[0], 30, 1, 90)
                limit = clamp_int(params.get("limit", [None])[0], 100, 1, MAX_LIMIT)
                search = params.get("q", [""])[0]
                foreign = params.get("foreign", ["0"])[0] == "1"
                payload = self.bridge_server.store.sources(days, limit, search, foreign)
            elif path == "/v1/targets":
                days = clamp_int(params.get("days", [None])[0], 30, 1, 90)
                limit = clamp_int(params.get("limit", [None])[0], 100, 1, MAX_LIMIT)
                payload = self.bridge_server.store.targets(days, limit)
            elif path == "/v1/geo":
                days = clamp_int(params.get("days", [None])[0], 30, 1, 90)
                payload = self.bridge_server.store.geo(days)
            elif path == "/v1/live":
                limit = clamp_int(params.get("limit", [None])[0], 150, 1, MAX_LIMIT)
                payload = self.bridge_server.store.live(limit)
            elif path == "/v1/alerts":
                limit = clamp_int(params.get("limit", [None])[0], 100, 1, MAX_LIMIT)
                payload = self.bridge_server.store.alerts(limit)
            elif path == "/v1/blocklist":
                payload = self.bridge_server.store.blocklist()
            elif path == "/v1/alert-rules":
                payload = self.bridge_server.store.alert_rules()
            else:
                json_response(self, 404, {"error": "not found"})
                return
            json_response(self, 200, payload)
        except (OSError, sqlite3.Error) as error:
            LOG.warning("traffic query failed: %s", error)
            json_response(self, 503, {"error": "traffic data unavailable"})
        except Exception:
            LOG.exception("unexpected bridge error")
            json_response(self, 500, {"error": "bridge error"})

    def do_POST(self) -> None:  # noqa: N802
        try:
            content_length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            json_response(self, 400, {"error": "invalid content length"})
            return
        if content_length < 0 or content_length > MAX_BODY_BYTES:
            json_response(self, 413, {"error": "request body too large"})
            return
        body_bytes = self.rfile.read(content_length)
        if not self._authorized(body_bytes):
            json_response(self, 401, {"error": "unauthorized"})
            return
        try:
            body = json.loads(body_bytes or b"{}")
        except (TypeError, ValueError, json.JSONDecodeError):
            json_response(self, 400, {"error": "invalid json body"})
            return
        if not isinstance(body, dict):
            json_response(self, 400, {"error": "json body must be an object"})
            return
        path = self._route_path()
        try:
            if path == "/v1/block":
                payload = self.bridge_server.store.block_ip(body.get("ip"), body.get("reason", ""))
            elif path == "/v1/unblock":
                payload = self.bridge_server.store.unblock_ip(body.get("ip"))
            elif path == "/v1/label":
                payload = self.bridge_server.store.label_ip(body.get("ip"), body.get("label", ""))
            elif path == "/v1/alerts/ack":
                payload = self.bridge_server.store.acknowledge_alert(body.get("id"), body.get("all", False))
            elif path == "/v1/alert-rules":
                payload = self.bridge_server.store.save_alert_rule(body)
            elif path == "/v1/alert-rules/delete":
                payload = self.bridge_server.store.delete_alert_rule(body.get("id"))
            elif path == "/v1/blocklist/sync":
                payload = self.bridge_server.store.sync_blocklist()
            elif path == "/v1/collect":
                payload = self.bridge_server.store.collect()
            elif path == "/v1/geo/refresh":
                payload = self.bridge_server.store.refresh_geo()
            else:
                json_response(self, 404, {"error": "not found"})
                return
            json_response(self, 200 if payload.get("ok", True) else 502, payload)
        except ValueError as error:
            json_response(self, 400, {"error": str(error)})
        except (OSError, sqlite3.Error) as error:
            LOG.warning("traffic control failed: %s", error)
            json_response(self, 503, {"error": "traffic control unavailable"})
        except Exception:
            LOG.exception("unexpected bridge control error")
            json_response(self, 500, {"error": "bridge error"})

    def _route_path(self) -> str:
        path = urlsplit(self.path).path
        if path.startswith("/internal/traffic/"):
            return path[len("/internal/traffic"):]
        return path


class BridgeServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        address: tuple[str, int],
        secret: str,
        store: TrafficStore,
        clock: Callable[[], int] = now_ts,
        max_skew: int = MAX_CLOCK_SKEW,
    ) -> None:
        super().__init__(address, BridgeHandler)
        self.secret = secret
        self.store = store
        self.clock = clock
        self.max_skew = max_skew


def main() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    secret = os.environ.get("XRAY_TRAFFIC_BRIDGE_SECRET", "").strip()
    if len(secret) < 32:
        raise SystemExit("XRAY_TRAFFIC_BRIDGE_SECRET must contain at least 32 characters")
    host = os.environ.get("XRAY_TRAFFIC_BRIDGE_HOST", DEFAULT_HOST)
    port = int(os.environ.get("XRAY_TRAFFIC_BRIDGE_PORT", DEFAULT_PORT))
    max_skew = int(os.environ.get("XRAY_TRAFFIC_BRIDGE_MAX_SKEW_SECONDS", MAX_CLOCK_SKEW))
    store = TrafficStore(
        db_path=os.environ.get("XRAY_TRAFFIC_DB", DEFAULT_DB_PATH),
        state_path=os.environ.get("XRAY_TRAFFIC_STATE", DEFAULT_STATE_PATH),
    )
    server = BridgeServer((host, port), secret, store, max_skew=max_skew)
    LOG.info("listening on %s:%s", host, port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
