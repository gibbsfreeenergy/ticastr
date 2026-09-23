#!/bin/sh

set -eu

certificate_dir="/etc/letsencrypt/live/api.ticastr.cn"
openresty_ssl_dir="/opt/1panel/apps/openresty/openresty/conf/ssl/ticastr"

install -m 0644 "$certificate_dir/fullchain.pem" "$openresty_ssl_dir/api.ticastr.cn.fullchain.pem"
install -m 0600 "$certificate_dir/privkey.pem" "$openresty_ssl_dir/api.ticastr.cn.privkey.pem"

docker exec openresty nginx -t
docker exec openresty nginx -s reload
