# ticastr production D1 API

This Worker is the production data plane for the administrator console. It
uses the existing `ticastr` D1 database, KV namespace, and R2 bucket. The
handler is shared with the tested D1 API implementation so the production
deployment cannot silently drift from the verified behavior.

The administrator Pages project proxies `/api/*` to this Worker through its
`API_ORIGIN` Pages secret. The traffic bridge secret and cursor signing secret
are Wrangler secrets and must never be committed.

```powershell
npx wrangler@4 deploy --config wrangler.jsonc
```
