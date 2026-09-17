# 部署契约

## 拓扑

| 文件 | 用途 | 默认服务 |
| --- | --- | --- |
| `compose.yaml` | 本地整合 | MySQL、API、公共前端、管理前端 |
| `compose.redis.yaml` | Redis overlay | 在上面增加 Redis |
| `deploy/backend/compose.yaml` | production-like API | MySQL、API |
| `deploy/backend/compose.redis.yaml` | production-like Redis overlay | 增加 Redis并启用 Redis adapter |

默认 Compose 不启动 RabbitMQ 或 Elasticsearch；Redis 只有通过 overlay 才进入依赖图。

## 共享不变量

1. API 由 Flyway 管理 schema，MySQL healthy 后才启动。
2. 浏览器只访问相对 `/api` 和 `/uploads`；代理去除 `/api` 前缀。
3. `APP_REDIS_ENABLED=false` 时 API 必须能在没有 Redis server 的情况下完成核心读写；启用 Redis 时共享 Session 由 `SPRING_SESSION_STORE_TYPE` 明确决定。
4. 运行时存储事实源是数据库中的 `tb_storage_provider_config`；active 档案只能有一条。
5. 新文章/媒体写入 active 档案，并保存 `provider` 与 `storage_config_id`；历史对象继续按记录读取。
6. 搜索索引目录必须位于持久数据目录，损坏或删除后可从公开文章内容重建。
7. production-like 必须使用真实 HTTPS origin、强 cursor secret、监控 token 和可用的 active 存储档案。

## 验证与回滚

```bash
bash scripts/verify-compose-contract.sh
bash scripts/verify-proxy-contract.sh
bash scripts/verify-architecture.sh
docker compose config
docker compose -f compose.yaml -f compose.redis.yaml config
```

API 发布回滚使用上一版本镜像，但不回滚已执行的 Flyway migration；schema 变更必须前向兼容。V26 migration 负责把 live schema、数据和管理员菜单收敛到核心博客能力。Redis 可直接去掉 overlay，Outbox 会回到 DB worker。
