#!/usr/bin/env python3
"""Read-only bridge for the local xray-dash SQLite database.

The bridge deliberately exposes only aggregate/read-only data. It does not
reuse xray-dash's browser session and has no write endpoints.
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
        return True


class TrafficStore:
    def __init__(
        self,
        db_path: str = DEFAULT_DB_PATH,
        state_path: str = DEFAULT_STATE_PATH,
        clock: Callable[[], int] = now_ts,
        xray_status: Callable[[], str] | None = None,
    ) -> None:
        self.db_path = db_path
        self.state_path = state_path
        self.clock = clock
        self.xray_status = xray_status or read_xray_status

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(
            f"file:{self.db_path}?mode=ro", uri=True, timeout=10
        )
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout=10000")
        return connection

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

        state = self._state()
        collector_at = state.get("at")
        collector_lag = self.clock() - int(collector_at) if collector_at else None
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

    def _authorized(self) -> bool:
        timestamp = self.headers.get("X-Ticastr-Traffic-Timestamp", "")
        signature = self.headers.get("X-Ticastr-Traffic-Signature", "")
        try:
            parsed_timestamp = int(timestamp)
        except ValueError:
            return False
        if abs(self.bridge_server.clock() - parsed_timestamp) > self.bridge_server.max_skew:
            return False
        canonical = f"{self.command}\n{self.path}\n{timestamp}".encode("utf-8")
        expected = hmac.new(
            self.bridge_server.secret.encode("utf-8"), canonical, hashlib.sha256
        ).hexdigest()
        return hmac.compare_digest(expected, signature)

    def do_GET(self) -> None:  # noqa: N802
        if not self._authorized():
            json_response(self, 401, {"error": "unauthorized"})
            return
        try:
            path = urlsplit(self.path).path
            if path.startswith("/internal/traffic/"):
                path = path[len("/internal/traffic"):]
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
            else:
                json_response(self, 404, {"error": "not found"})
                return
            json_response(self, 200, payload)
        except (OSError, sqlite3.Error) as error:
            LOG.warning("read-only traffic query failed: %s", error)
            json_response(self, 503, {"error": "traffic data unavailable"})
        except Exception:
            LOG.exception("unexpected bridge error")
            json_response(self, 500, {"error": "bridge error"})

    def do_POST(self) -> None:  # noqa: N802
        json_response(self, 405, {"error": "read-only endpoint"})


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
