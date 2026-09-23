#!/usr/bin/env bash
set -euo pipefail

for file in blog-vue/blog/nginx.conf blog-vue/admin/nginx.conf; do
  rg -q 'location /api/|proxy_pass http://api:8090/;' "$file"
  rg -q 'location /uploads/|proxy_pass http://api:8090/uploads/;' "$file"
  rg -q '^  client_max_body_size 20m;$' "$file"
  rg -q 'try_files \$uri \$uri/ /index.html;' "$file"
done

rg -q 'add_header Cache-Control "no-cache" always;' blog-vue/blog/nginx.conf
rg -q 'add_header Cache-Control "no-store" always;' blog-vue/admin/nginx.conf
test ! -f blog-vue/blog/vercel.json
test ! -f blog-vue/admin/vercel.json
test -f blog-vue/blog/vercel.mjs
test -f blog-vue/admin/vercel.mjs

for file in blog-vue/blog/wrangler.jsonc blog-vue/admin/wrangler.jsonc; do
  rg -q '"pages_build_output_dir"[[:space:]]*:[[:space:]]*"dist"' "$file"
done

for file in blog-vue/blog/cloudflare-proxy.js blog-vue/admin/cloudflare-proxy.js; do
  rg -q 'env\?\.API_ORIGIN' "$file"
done

for file in \
  'blog-vue/blog/functions/api/[[path]].js' \
  'blog-vue/blog/functions/uploads/[[path]].js' \
  'blog-vue/admin/functions/api/[[path]].js' \
  'blog-vue/admin/functions/uploads/[[path]].js'; do
  test -f "$file"
done

for file in blog-vue/blog/public/_routes.json blog-vue/admin/public/_routes.json; do
  rg -q '"/api/\*"' "$file"
  rg -q '"/uploads/\*"' "$file"
done
