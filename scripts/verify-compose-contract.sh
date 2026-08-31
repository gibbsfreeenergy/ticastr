#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null 2>&1 || {
  echo "docker is required for compose contract validation" >&2
  exit 1
}

docker compose --env-file .env.example -f compose.yaml config --quiet
docker compose --env-file .env.example -f compose.yaml -f compose.redis.yaml config --quiet
docker compose --env-file deploy/backend/.env.example -f deploy/backend/compose.yaml config --quiet
docker compose --env-file deploy/backend/.env.example -f deploy/backend/compose.yaml -f deploy/backend/compose.redis.yaml config --quiet

for file in compose.yaml deploy/backend/compose.yaml; do
  if grep -q 'blog-mysql8\.sql' "$file"; then
    echo "$file still mounts the removed database dump" >&2
    exit 1
  fi
  grep -q 'condition: service_healthy' "$file"
  grep -q 'MONITORING_TOKEN' "$file"
  grep -q 'BOOTSTRAP_ADMIN_ENABLED' "$file"
  grep -q 'healthcheck:' "$file"
done

for file in compose.redis.yaml deploy/backend/compose.redis.yaml; do
  grep -q 'APP_REDIS_ENABLED' "$file"
  grep -q 'service_healthy' "$file"
done

if rg -n -i 'rabbitmq|RABBIT|elasticsearch|SEARCH_MODE|UPLOAD_MODE' \
    compose.yaml compose.redis.yaml deploy/backend/compose.yaml deploy/backend/compose.redis.yaml; then
  echo "Compose files still reference removed RabbitMQ/Elasticsearch/upload-mode contracts" >&2
  exit 1
fi

echo "Compose contract checks passed"
