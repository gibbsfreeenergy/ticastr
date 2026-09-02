# 部署契约

## 拓扑

| 文件 | 用途 | 默认服务 |
| --- | --- | --- |
| compose.yaml | 本地整合 | MySQL、API、公共前端、管理前端 |
| compose.redis.yaml | Redis overlay | 在上面增加 Redis，并让 API 启用 Redis |
| deploy/backend/compose.yaml | production-like API | MySQL、API |
| deploy/backend/compose.redis.yaml | production-like Redis overlay | 增加 Redis、共享 Redis 配置 |

默认 Compose 不启动 Redis、RabbitMQ 或 Elasticsearch；Redis 只有通过 overlay 才进入依赖图。API base health/readiness 只等待数据库，Redis-enabled overlay 才等待 Redis。

## 共享不变量

1. API 由 Flyway 管理 schema，MySQL healthy 后才启动。
2. 浏览器只访问相对 /api、/uploads、/websocket；代理去除 /api 前缀并保留 WebSocket upgrade。
3. APP_REDIS_ENABLED=false 时应用必须能在没有 Redis server 的情况下启动并完成核心读写；启用 Redis 时 session 是否共享由 SPRING_SESSION_STORE_TYPE 明确决定。
4. 运行时存储事实源是数据库中的 `tb_storage_provider_config`；provider 仅 `local`、`cos`、`oss`、`tos`，同 provider 可有多条档案，但 `is_active` 只能有一条。
5. 旧 `STORAGE_ACTIVE_PROVIDER`、`STORAGE_LOCAL_*`、`OSS_*`、`COS_*`、`TOS_*` 只在 bootstrap 未完成时导入一次；`tb_storage_bootstrap_state` 完成后不再覆盖数据库配置。
6. catalog 中存在 cloud profile 时必须提供 `STORAGE_CONFIG_ENCRYPTION_KEY`，其值必须是 Base64 编码的 32 字节 AES 密钥；local-only 部署可省略。
7. 新文章/媒体写入当前 active 档案，并同时保存 `provider` 与 `storage_config_id`；切换 active 只影响新对象，历史对象继续按记录中的配置读取/删除。
8. 搜索索引目录必须是持久数据目录，损坏或删除后可从公开对象内容重建。
9. production-like 必须使用真实 HTTPS origin、强游标密钥、监控 token 和至少一个可用 active 档案；禁止 placeholder。

## 验证与回滚

```bash
bash scripts/verify-compose-contract.sh
bash scripts/verify-proxy-contract.sh
bash scripts/verify-architecture.sh
docker compose config
docker compose -f compose.yaml -f compose.redis.yaml config
```

API 发布回滚使用上一版本镜像，但不回滚已执行的 Flyway migration；schema 变更必须前向兼容。V21 migration 负责写入 `/admin/storage/configs*` 权限资源和滚动兼容路由依赖的数据。Redis 可直接去掉 overlay，Outbox 会回到 DB worker；active 存储档案切换前必须通过后台写/读/删验证，旧 provider-only 路由只在目标 provider 能唯一映射到一条档案时可用。
