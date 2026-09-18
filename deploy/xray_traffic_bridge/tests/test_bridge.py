import hashlib
import hmac
import importlib.util
import json
import sqlite3
import sys
import tempfile
import threading
import time
import unittest
from http.client import HTTPConnection
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "bridge.py"
SPEC = importlib.util.spec_from_file_location("xray_traffic_bridge", MODULE_PATH)
bridge = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = bridge
SPEC.loader.exec_module(bridge)


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.db_path = Path(self.temp_dir.name) / "traffic.db"
        self.state_path = Path(self.temp_dir.name) / "state.json"
        self._create_db()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _create_db(self):
        connection = sqlite3.connect(self.db_path)
        connection.executescript(
            """
            CREATE TABLE conns(ts INTEGER, hour INTEGER, day INTEGER, src TEXT, sport INTEGER,
                net TEXT, host TEXT, port INTEGER, inb TEXT, outb TEXT);
            CREATE TABLE ip_meta(ip TEXT PRIMARY KEY, cc TEXT, prov TEXT, city TEXT, org TEXT,
                first_seen INTEGER, last_seen INTEGER, n INTEGER, label TEXT, blocked INTEGER);
            CREATE TABLE agg_hour(hour INTEGER, src TEXT, n INTEGER);
            CREATE TABLE traffic(ts INTEGER PRIMARY KEY, up INTEGER, down INTEGER, online INTEGER);
            CREATE TABLE alerts(id INTEGER PRIMARY KEY, ts INTEGER, level TEXT, kind TEXT,
                title TEXT, detail TEXT, acked INTEGER);
            CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT);
            CREATE TABLE scan_state(k TEXT PRIMARY KEY, v TEXT);
            INSERT INTO settings VALUES ('exclude_local', '1');
            """
        )
        now = 1_800_000_000
        hour = bridge.hour_key(now)
        day = bridge.day_key(now)
        connection.executemany(
            "INSERT INTO conns VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (now, hour, day, "8.8.8.8", 1, "tcp", "example.com", 443, "", "proxy"),
                (now - 30, hour, day, "8.8.8.8", 2, "tcp", "example.org", 443, "", "proxy"),
                (now - 60, hour, day, "10.0.0.1", 3, "tcp", "internal", 80, "", "proxy"),
            ],
        )
        connection.execute(
            "INSERT INTO ip_meta VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ("8.8.8.8", "US", "", "", "Example ISP", now - 60, now, 2, "", 0),
        )
        connection.execute(
            "INSERT INTO ip_meta VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ("10.0.0.1", "LO", "", "", "Local", now - 60, now, 1, "", 0),
        )
        connection.executemany(
            "INSERT INTO agg_hour VALUES (?, ?, ?)",
            [(hour, "8.8.8.8", 2)],
        )
        connection.executemany(
            "INSERT INTO traffic VALUES (?, ?, ?, ?)",
            [(now - 30, 100, 200, 1), (now, 150, 260, 2)],
        )
        connection.execute(
            "INSERT INTO alerts VALUES (?, ?, ?, ?, ?, ?, ?)",
            (1, now, "high", "test", "Test alert", "Read-only test", 0),
        )
        connection.commit()
        connection.close()
        self.state_path.write_text(json.dumps({"at": now}), encoding="utf-8")

    def test_queries_are_read_only_and_mask_local_data_from_aggregates(self):
        store = bridge.TrafficStore(
            str(self.db_path), str(self.state_path), clock=lambda: 1_800_000_000,
            xray_status=lambda: "active",
        )
        overview = store.overview()
        self.assertEqual(overview["total_conns"], 3)
        self.assertEqual(overview["total_ips"], 1)
        self.assertEqual(overview["online"], 2)
        self.assertEqual(store.sources(30, 10)["total"], 1)
        self.assertIn("example.com", {item["host"] for item in store.targets(30, 10)})
        self.assertEqual(store.live(10)[0]["src"], "8.8.8.8")
        with sqlite3.connect(self.db_path) as connection:
            self.assertEqual(connection.execute("SELECT COUNT(*) FROM alerts").fetchone()[0], 1)

    def test_overview_prefers_latest_traffic_sample_over_stale_state_file(self):
        with sqlite3.connect(self.db_path) as connection:
            connection.execute("UPDATE traffic SET ts=? WHERE ts=?", (1_799_999_995, 1_800_000_000))
            connection.commit()
        self.state_path.write_text(json.dumps({"at": 1_799_996_400}), encoding="utf-8")

        store = bridge.TrafficStore(
            str(self.db_path), str(self.state_path), clock=lambda: 1_800_000_000,
            xray_status=lambda: "active",
        )

        collector = store.overview()["collector"]
        self.assertEqual(collector["at"], 1_799_999_995)
        self.assertEqual(collector["lag"], 5)

    def test_overview_falls_back_to_state_file_without_traffic_samples(self):
        with sqlite3.connect(self.db_path) as connection:
            connection.execute("DELETE FROM traffic")
            connection.commit()
        self.state_path.write_text(json.dumps({"at": 1_799_999_970}), encoding="utf-8")

        store = bridge.TrafficStore(
            str(self.db_path), str(self.state_path), clock=lambda: 1_800_000_000,
            xray_status=lambda: "active",
        )

        collector = store.overview()["collector"]
        self.assertEqual(collector["at"], 1_799_999_970)
        self.assertEqual(collector["lag"], 30)

    def test_hmac_request_round_trip(self):
        secret = "s" * 40
        store = bridge.TrafficStore(
            str(self.db_path), str(self.state_path), clock=lambda: 1_800_000_000,
            xray_status=lambda: "active",
        )
        server = bridge.BridgeServer(("127.0.0.1", 0), secret, store, clock=lambda: 1_800_000_000)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            port = server.server_address[1]
            target = "/v1/overview"
            timestamp = str(1_800_000_000)
            signature = hmac.new(
                secret.encode(), f"GET\n{target}\n{timestamp}".encode(), hashlib.sha256
            ).hexdigest()
            connection = HTTPConnection("127.0.0.1", port, timeout=5)
            connection.request(
                "GET", target,
                headers={
                    "X-Ticastr-Traffic-Timestamp": timestamp,
                    "X-Ticastr-Traffic-Signature": signature,
                },
            )
            response = connection.getresponse()
            payload = json.loads(response.read())
            self.assertEqual(response.status, 200)
            self.assertEqual(payload["xray"], "active")
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_prefixed_request_keeps_original_path_in_signature(self):
        secret = "s" * 40
        store = bridge.TrafficStore(
            str(self.db_path), str(self.state_path), clock=lambda: 1_800_000_000,
            xray_status=lambda: "active",
        )
        server = bridge.BridgeServer(("127.0.0.1", 0), secret, store, clock=lambda: 1_800_000_000)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            port = server.server_address[1]
            target = "/internal/traffic/v1/health"
            timestamp = str(1_800_000_000)
            signature = hmac.new(
                secret.encode(), f"GET\n{target}\n{timestamp}".encode(), hashlib.sha256
            ).hexdigest()
            connection = HTTPConnection("127.0.0.1", port, timeout=5)
            connection.request(
                "GET", target,
                headers={
                    "X-Ticastr-Traffic-Timestamp": timestamp,
                    "X-Ticastr-Traffic-Signature": signature,
                },
            )
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertEqual(json.loads(response.read())["db"], "ok")
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_missing_signature_is_rejected(self):
        store = bridge.TrafficStore(str(self.db_path), str(self.state_path))
        server = bridge.BridgeServer(("127.0.0.1", 0), "s" * 40, store)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = HTTPConnection("127.0.0.1", server.server_address[1], timeout=5)
            connection.request("GET", "/v1/health")
            response = connection.getresponse()
            self.assertEqual(response.status, 401)
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
