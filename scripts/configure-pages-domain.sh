#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <account-id> <project-name> <domain-name>" >&2
  exit 2
fi

account_id="$1"
project_name="$2"
domain_name="$3"
api_url="https://api.cloudflare.com/client/v4/accounts/${account_id}/pages/projects/${project_name}/domains"
auth_header="Authorization: Bearer ${CLOUDFLARE_API_TOKEN:?CLOUDFLARE_API_TOKEN is required}"

domains_response="$(curl --fail-with-body --silent --show-error \
  --header "$auth_header" \
  "$api_url")"

if printf '%s' "$domains_response" | jq -e --arg domain "$domain_name" \
  '.success == true and any(.result[]?; .name == $domain)' >/dev/null; then
  echo "Cloudflare Pages custom domain already configured: ${domain_name}"
  exit 0
fi

payload="$(jq -cn --arg name "$domain_name" '{name: $name}')"
create_response="$(curl --fail-with-body --silent --show-error \
  --request POST \
  --header "$auth_header" \
  --header "Content-Type: application/json" \
  --data "$payload" \
  "$api_url")"

if ! printf '%s' "$create_response" | jq -e --arg domain "$domain_name" \
  '.success == true and .result.name == $domain' >/dev/null; then
  echo "Cloudflare Pages custom domain setup failed for ${domain_name}:" >&2
  printf '%s\n' "$create_response" >&2
  exit 1
fi

status="$(printf '%s' "$create_response" | jq -r '.result.status // "unknown"')"
echo "Cloudflare Pages custom domain configured: ${domain_name} (status: ${status})"
