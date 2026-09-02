# 运维手册

## 启动

1. 校验 .env 中 DB、origin、CORS、监控 token、游标密钥，以及是否需要 `STORAGE_CONFIG_ENCRYPTION_KEY`。
2. 默认模式启动：docker compose up -d。
3. Redis 模式启动：docker compose -f compose.yaml -f compose.redis.yaml up -d。
4. 检查 /actuator/health/liveness、/actuator/health/readiness；再访问公共站点、后台和 WebSocket。

Redis readiness 只在 Redis-enabled profile 中作为依赖。默认模式不应因 Redis 缺失而阻塞 API。

## 监控

通过 API 使用 X-Monitoring-Token 抓取 /actuator/prometheus。重点指标：HTTP p50/p95、Hikari 使用率、Outbox pending/processing/dead、最老事件、handler 失败、Stream pending/dead、Lucene index 状态、provider 验证/删除/usage 失败、限流拒绝和 WebSocket 连接数。

## Outbox/Streams 故障

- 先检查 event type、status、attempts、last_error 和 traceId。
- PENDING 会由 DB worker 自动重试；Redis 不可用时不要手动删除事件。
- DEAD 先修复 handler/provider，再在后台 Outbox 页面对单事件执行 retry。
- Redis Streams pending 由 consumer recovery 重新 claim；handler 成功前不得 ACK。
- 重试仍以 eventId 幂等，不能通过重复点击制造业务副作用。

## Redis 模式切换

- 关闭：去掉 overlay，设置 APP_REDIS_ENABLED=false；确认普通 servlet session 和本地 fallback 可用。
- 开启：Redis healthy 后叠加 overlay；若需要多实例登录，设置 SPRING_SESSION_STORE_TYPE=redis。
- Redis 停止后核心事实读写应继续，缓存/presence/广播可降级，Outbox 不得丢失。

## 对象存储

后台“基础设施”页以 `/api/admin/storage/configs` 为主接口管理托管档案：

- provider 仅 `local`、`cos`、`oss`、`tos`；同 provider 可保留多条档案，但数据库 `is_active` 只允许一条 active。
- `local` 档案要求 `localRoot` 与 `publicUrl`；cloud 档案要求 `endpoint`、`region`、`bucket`、`publicUrl`、`accessKeyId`、`accessKeySecret`，并且 catalog 中只要存在 cloud profile 行就必须配置 `STORAGE_CONFIG_ENCRYPTION_KEY`。
- 旧 `STORAGE_ACTIVE_PROVIDER`、`STORAGE_LOCAL_*`、`OSS_*`、`COS_*`、`TOS_*` 只在 bootstrap 未完成时导入一次，并以 `LEGACY_ENV` source 落库。`tb_storage_bootstrap_state.legacy_import_completed = 1` 后，数据库是唯一运行时事实源，继续改这些环境变量不会覆盖档案。
- 先执行 `/validate`，再执行 `/activate`。验证接口失败时返回安全快照，不返回供应商异常细节；激活失败返回 409，当前 active 档案保持不变。
- `/usage` 只做手动刷新。失败时返回 `FAILED` 状态和安全错误，同时保留上次成功的对象数、字节数和最近修改时间。
- active 档案不能删除；仍被 `tb_content_asset` 或 `tb_media_asset` 引用的档案不能删除。
- 旧 `/api/admin/storage/provider`、`/providers` 和 provider-only validate/switch 路由仅为滚动兼容保留；当同 provider 存在多条档案时，旧 validate/switch 返回 409，要求改用配置 ID。
- 权限资源由 V21 migration 写入数据库；如果缺少 `/admin/storage/configs*` 权限，先核查迁移和资源表，不在 controller 或文档里手工补“配置”。

## 搜索重建

停止或隔离 API 的搜索写入后备份索引目录，删除/移动 SEARCH_INDEX_PATH，调用管理员重建任务或重启触发 rebuild，再检查文章标题/标签/正文搜索。重建从对象存储读取 Markdown，不从 MySQL 恢复正文。

## 浏览器/SEO 发布

公共站点构建需要 PUBLIC_SITE_ORIGIN；若生成静态文章失败，构建必须失败而不是发布空壳。部署后检查 /robots.txt、/sitemap.xml、/feed.xml、/articles/{id}/ 的 title/canonical/H1/JSON-LD 和未知路由 fallback。CDN 更新时只清理 HTML/feeds，静态 hash 资源可长期缓存。

## 数据安全

不得在日志、错误响应、SEO 文件或 admin API 输出密码、access key、secret、AES-GCM 密文、Authorization、签名、完整 provider endpoint、任意 object key 或请求正文。发现泄露时先轮换凭据，再保留 traceId 调查。
