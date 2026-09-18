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
8. 启用代理监控时，`XRAY_TRAFFIC_BASE_URL` 必须指向 DMIT Caddy 的 `/internal/traffic` 路由，`XRAY_TRAFFIC_SHARED_SECRET` 必须与 DMIT bridge 环境文件一致；共享密钥只存在于两台服务器。

## 验证与回滚

```bash
bash scripts/verify-compose-contract.sh
bash scripts/verify-proxy-contract.sh
bash scripts/verify-architecture.sh
docker compose config
docker compose -f compose.yaml -f compose.redis.yaml config
```

API 发布回滚使用上一版本镜像，但不回滚已执行的 Flyway migration；schema 变更必须前向兼容。V26 migration 负责把 live schema、数据和管理员菜单收敛到核心博客能力。Redis 可直接去掉 overlay，Outbox 会回到 DB worker。

## GitHub Actions 自动部署

`.github/workflows/deploy.yml` 在 `master` 推送或手动触发时执行：

1. 使用专用 SSH 用户同步源码到部署目录。
2. 保留服务器上的 `.env`、上传文件和数据库/搜索 Docker volume。
3. 在服务器上执行 `docker compose up -d --build --remove-orphans`。
4. 等待 API readiness、公共站点和管理端通过检查。

仓库需要配置以下 Secrets：

- `TICASTR_DEPLOY_HOST`
- `TICASTR_DEPLOY_PORT`
- `TICASTR_DEPLOY_USER`
- `TICASTR_DEPLOY_PATH`
- `TICASTR_DEPLOY_SSH_PRIVATE_KEY`
- `TICASTR_DEPLOY_KNOWN_HOSTS`

服务器的 `.env` 只保留在部署主机，不由 Actions 覆盖；首次管理员 bootstrap 成功后应关闭
`BOOTSTRAP_ADMIN_ENABLED` 并清除明文 bootstrap 密码。

DMIT 代理监控桥接服务的部署文件位于 [`deploy/xray_traffic_bridge/`](../deploy/xray_traffic_bridge/)。它绑定
`127.0.0.1:8788`，通过 Caddy 的 HTTPS `/internal/traffic/*` 路由提供 HMAC 签名的只读接口，不能写入
xray-dash 数据库，也不会暴露原看板的登录凭据或写操作。
