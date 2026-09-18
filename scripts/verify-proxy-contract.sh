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
