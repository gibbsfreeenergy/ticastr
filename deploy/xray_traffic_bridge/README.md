# Xray traffic bridge

This service exposes a small, authenticated HTTP API over the local xray-dash
SQLite database. It is intended for the ticastr API, not for direct browser
access.

Read routes use a SQLite `mode=ro` connection. The first-stage control routes
open a short-lived write connection and are limited to IP block/unblock,
labels, alert acknowledgement/rules, blacklist sync and collector/GeoIP
refresh. The bridge validates an HMAC signature on every request, and POST
signatures also cover the SHA-256 digest of the exact request body.

## Runtime configuration

The systemd unit expects `/etc/xray-dash/traffic-bridge.env`:

```text
XRAY_TRAFFIC_BRIDGE_SECRET=<at-least-32-random-characters>
XRAY_TRAFFIC_DB=/opt/xray-dash/data/traffic.db
XRAY_TRAFFIC_STATE=/opt/xray-dash/data/state.json
XRAY_TRAFFIC_BRIDGE_HOST=127.0.0.1
XRAY_TRAFFIC_BRIDGE_PORT=8788
```

The secret must be identical to `XRAY_TRAFFIC_SHARED_SECRET` in the ticastr
API environment. It must never be committed to Git.

The overview collector heartbeat uses the newest `traffic.ts` sample from the
database. `XRAY_TRAFFIC_STATE` is retained as a compatibility fallback for
older xray-dash installations that still update `state.json`.

## Endpoints

All endpoints require `X-Ticastr-Traffic-Timestamp` and
`X-Ticastr-Traffic-Signature` headers. The signature for GET is
`HMAC-SHA256(method + "\\n" + request-target + "\\n" + timestamp)`.
For POST it is
`HMAC-SHA256(method + "\\n" + request-target + "\\n" + timestamp + "\\n" + body-sha256)`.
POST requests must also include `X-Ticastr-Traffic-Body-SHA256`.

```text
GET /v1/health
GET /v1/overview
GET /v1/timeseries?hours=48
GET /v1/daily?days=30
GET /v1/sources?days=30&limit=100
GET /v1/targets?days=30&limit=100
GET /v1/geo?days=30
GET /v1/live?limit=150
GET /v1/alerts?limit=100
GET /v1/blocklist
GET /v1/alert-rules

POST /v1/block                  {"ip":"8.8.8.8","reason":"..."}
POST /v1/unblock                {"ip":"8.8.8.8"}
POST /v1/label                  {"ip":"8.8.8.8","label":"..."}
POST /v1/alerts/ack             {"id":1} or {"all":true}
POST /v1/alert-rules            {"id":"burst-main", ...}
POST /v1/alert-rules/delete     {"id":"burst-main"}
POST /v1/blocklist/sync
POST /v1/collect
POST /v1/geo/refresh
```

The bridge runs as `xray-dash`, so it can write only within the existing
xray-dash data directory and can invoke the local Xray API for dynamic source
IP rules. It never exposes the Xray API or xray-dash browser session publicly.
