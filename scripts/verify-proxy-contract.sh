#!/usr/bin/env bash
set -euo pipefail

for file in blog-vue/blog/nginx.conf blog-vue/admin/nginx.conf; do
  rg -q 'location /api/|proxy_pass http://api:8090/;' "$file"
  rg -q 'location /uploads/|proxy_pass http://api:8090/uploads/;' "$file"
  rg -q 'location /websocket|proxy_http_version 1.1|proxy_set_header Upgrade' "$file"
  rg -q 'try_files \$uri \$uri/ /index.html;' "$file"
done

cmp -s blog-vue/blog/nginx.conf blog-vue/admin/nginx.conf
test ! -f blog-vue/blog/vercel.json
test ! -f blog-vue/admin/vercel.json
test -f blog-vue/blog/vercel.mjs
test -f blog-vue/admin/vercel.mjs
