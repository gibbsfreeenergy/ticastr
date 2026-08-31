#!/usr/bin/env bash
set -euo pipefail

fail=0

if rg -n '\$http' blog-vue/blog/src blog-vue/admin/src; then
  echo 'Legacy $http access is not allowed in frontend source.' >&2
  fail=1
fi

if rg -n '\$api\.(get|post|put|delete|request)[[:space:]]*\(' \
    blog-vue/blog/src blog-vue/admin/src; then
  echo 'Frontend views/components must use domain API methods, not transport methods.' >&2
  fail=1
fi

if rg -n '(["'"'"'`])/(api|uploads|websocket)(/|["'"'"'`])' \
    blog-vue/blog/src/views blog-vue/blog/src/components \
    blog-vue/admin/src/views blog-vue/admin/src/components; then
  echo 'Frontend views/components must not own proxy endpoint paths.' >&2
  fail=1
fi

if rg -n 'import\.meta\.glob' blog-vue/admin/src; then
  echo 'Dynamic menu component discovery must use the explicit route registry.' >&2
  fail=1
fi

if rg -n 'StringRedisTemplate' blog-springboot/src/main/java/com/wzh/blog/service; then
  echo 'Domain service packages must use typed Redis ports.' >&2
  fail=1
fi

if rg -n -U '@(Autowired|Resource)[[:space:]]*\r?\n[[:space:]]*(private|protected)[[:space:]]' blog-springboot/src/main/java/com/wzh/blog; then
  echo 'Spring dependencies must use constructor injection, not field injection.' >&2
  fail=1
fi

if rg -n 'PaginationContext' blog-springboot/src/main/java/com/wzh/blog/service \
    blog-springboot/src/main/java/com/wzh/blog/content; then
  echo 'Application and domain services must receive PageQuery explicitly.' >&2
  fail=1
fi

if rg -n 'article_content' blog-springboot/src/main/java blog-springboot/src/main/resources/mapper; then
  echo 'Runtime article metadata/mapper code must not depend on article_content.' >&2
  fail=1
fi

if rg -n 'SELECT[[:space:]]+\*|select[[:space:]]+\*' \
    blog-springboot/src/main/resources/mapper; then
  echo 'Runtime mappers must select explicit columns.' >&2
  fail=1
fi

if rg -n 'localhost:8090|https?://[^"'"'"'` ]*oss|https?://[^"'"'"'` ]*cos|https?://[^"'"'"'` ]*tos' \
    blog-vue/blog/src/views blog-vue/blog/src/components \
    blog-vue/admin/src/views blog-vue/admin/src/components; then
  echo 'Frontend business components must not own backend/provider endpoints.' >&2
  fail=1
fi

if rg -n 'Rabbit|AMQP|Elasticsearch|spring\.elasticsearch|@Rabbit' \
    blog-springboot/src/main/java blog-springboot/src/main/resources \
    compose.yaml compose.redis.yaml deploy/backend; then
  echo 'Removed broker/search runtime integrations must not reappear.' >&2
  fail=1
fi

if rg -n 'static[[:space:]].*(CopyOnWriteArraySet|Set<.*Session|Map<.*Session)' \
    blog-springboot/src/main/java/com/wzh/blog; then
  echo 'WebSocket/session business state must not use static mutable collections.' >&2
  fail=1
fi

if [[ ! -f blog-vue/blog/vercel.mjs || ! -f blog-vue/admin/vercel.mjs ]]; then
  echo 'Both frontend deployments must use dynamic Vercel configuration.' >&2
  fail=1
fi

if ! cmp -s blog-vue/blog/nginx.conf blog-vue/admin/nginx.conf; then
  echo 'Frontend Nginx proxy contracts have drifted.' >&2
  fail=1
fi

exit "$fail"
