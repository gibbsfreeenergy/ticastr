# Xray traffic bridge

This service exposes a small, authenticated, read-only HTTP API over the
local xray-dash SQLite database. It is intended for the ticastr API, not for
direct browser access.

The bridge deliberately has no write routes. It reads the database through a
SQLite `mode=ro` connection and validates an HMAC signature on every request.
The systemd unit grants the process access to the data directory because
SQLite WAL mode maintains a shared-memory lock file there; the bridge itself
never opens the database for writes.

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
`X-Ticastr-Traffic-Signature` headers. The signature is
`HMAC-SHA256(method + "\\n" + request-target + "\\n" + timestamp)`.

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
```
