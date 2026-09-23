# ticastr test admin proxy

This Worker is an isolated validation entry point for the D1 + KV admin API.
It serves the existing `ticastr-admin` Pages project and proxies `/api/*` to
`ticastr-d1-api-test`. The custom domain is `admin-d1-test.ticastr.cn`.

It does not change `admin.ticastr.cn` or the production API. Remove or update
this proxy only after the D1 admin flow has been verified end to end.

```powershell
npx wrangler@4 deploy --config wrangler.jsonc
```
