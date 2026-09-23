# ticastr D1 API test Worker

This Worker is the test-only API/data plane for the Cloudflare migration.
It is deployed as `ticastr-d1-api-test` and is not the production API.

## Bindings

- `DB`: the migrated D1 database
- `ASSETS`: the `ticastr` R2 bucket
- `KV`: the session store for administrator sessions
- `CURSOR_SECRET`: Wrangler secret used to sign public pagination cursors

The session cookie is short-lived and refreshed on authenticated requests. The
CSRF token is stored in the `XSRF-TOKEN` cookie and must be sent as
`X-XSRF-TOKEN` for state-changing requests.

## Local commands

```powershell
npm ci
npx wrangler@4 deploy --config wrangler.jsonc
```

Administrator CRUD is available on the test Worker for articles, Markdown
content versions, pages, website configuration, the about page, profile and
password changes, and media uploads. Traffic-monitoring reads and the first
stage controls are signed through the existing DMIT traffic bridge; the bridge
secret is stored as the Wrangler secret `XRAY_TRAFFIC_SHARED_SECRET` and is
never committed. Storage-provider routes remain on the existing API.
