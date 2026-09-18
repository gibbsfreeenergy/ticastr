# 运维手册

## 启动

1. 校验 `.env` 中数据库、站点 origin、CORS、监控 token、cursor secret 和存储主密钥。
2. 默认模式启动：`docker compose up -d`。
3. Redis 模式启动：`docker compose -f compose.yaml -f compose.redis.yaml up -d`。
4. 检查 `/actuator/health/liveness`、`/actuator/health/readiness`，再访问公共站点和管理后台。

默认模式不应因 Redis 缺失而阻塞 API。首次初始化管理员后立即关闭 `BOOTSTRAP_ADMIN_ENABLED`。

## 监控

通过 API 使用 `X-Monitoring-Token` 抓取 `/actuator/prometheus`。重点指标：HTTP 延迟、Hikari 使用率、Outbox pending/processing/dead、handler 失败、Stream pending/dead、Lucene index 状态、限流拒绝和存储删除失败。托管存储问题优先查看 `/api/admin/storage/configs` 的验证/usage 快照、应用日志和 health 状态。

## Outbox/Streams 故障

- 先检查 event type、status、attempts、last_error 和 traceId。
- `PENDING` 会由 DB worker 自动重试；Redis 不可用时不要删除事件。
- Redis Streams pending 由 consumer recovery 重新 claim；handler 成功前不得 ACK。
- 重试仍以 eventId 幂等，不能通过重复操作制造业务副作用。

## Redis 模式切换

- 关闭：去掉 overlay，设置 `APP_REDIS_ENABLED=false`，确认普通 servlet session 和本地 fallback 可用。
- 开启：叠加 overlay；overlay 会等待 Redis healthy，并将 `SPRING_SESSION_STORE_TYPE` 设置为 `redis`，因此 API 重启后登录 Session 仍由 Redis 保留。
- Redis 停止后文章和配置事实读写应继续，缓存/限流/协调可按各自策略降级，Outbox 不得丢失。

## 对象存储

后台“媒体存储”（`/storage`）使用 `/api/admin/storage/configs` 管理托管档案：

- provider 仅 `local`、`cos`、`oss`、`tos`；active 档案不能删除。
- 先执行 `/validate`，再执行 `/activate`；失败时返回安全快照，不返回 SDK 细节。
- `/usage` 只做手动刷新；失败时保留上次成功的对象数、字节数和最近修改时间。
- 仍被 `tb_content_asset` 或 `tb_media_asset` 引用的配置不能删除。

## 搜索重建

API 启动时从公开文章元数据和 Markdown 内容重建本机 Lucene 索引。首次重建失败时 API 仍可启动，但搜索可能为空；修复存储后重启重试。索引可删除重建，不需要单独的管理端重建页面。

## 浏览器/SEO 发布

公共站点构建需要 `PUBLIC_SITE_ORIGIN`。部署后检查 `/robots.txt`、`/sitemap.xml`、`/feed.xml`、`/articles/{id}/` 的 title、canonical、H1 和 JSON-LD。

## 数据安全

不得在日志、错误响应、SEO 文件或 admin API 输出密码、access key、secret、AES-GCM 密文、Authorization、签名、供应商请求 URL、任意 object key 或请求正文。
