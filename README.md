# ticastr

个人博客系统：一个 Spring Boot API、一个公共博客 SPA 和一个管理后台 SPA。

## 架构概要

- API：Java 21、Spring Boot 4.1、Spring Security 7、MyBatis-Plus、Flyway、WebSocket。
- 事实源：MySQL 8。文章正文是 UTF-8 Markdown 对象，MySQL 只保存内容资产指针、版本、校验和及对象 provider。
- 异步：MySQL transactional outbox；Redis Streams 是可选加速传输，关闭或故障时由数据库 worker 执行同一组幂等 handler。
- 缓存/限流/锁/Session：Redis 可选；默认关闭时使用有界本地 fallback，不能绕过安全策略。
- 搜索：可重建的本地 Lucene 索引，不把正文重新存回 MySQL，也不依赖 Elasticsearch。
- 对象存储：同一时间只激活一个 provider，可选 local、阿里 OSS、腾讯 COS、火山 TOS；切换由后台完成验证，已有资产保留原 provider。
- 前端：Vue 3/Vite，公共站点默认 `8080`，管理后台默认 `8081`，API 默认 `8090`。

完整设计见 [`docs/ARCHITECTURE-ROADMAP.md`](docs/ARCHITECTURE-ROADMAP.md) 和已批准的实现计划 [`docs/superpowers/plans/`](docs/superpowers/plans/)。

## 目录

```text
.
|- blog-springboot/    Spring Boot API
|- blog-vue/blog/      公共博客
|- blog-vue/admin/     管理后台
|- blog-vue/shared/    两个 SPA 共用的 HTTP、内容、实时通信契约
|- database/           数据库初始化和迁移说明
`- docs/               API、部署、运维和架构契约
```

## 本地启动

1. 准备一个空的 MySQL 数据库，或运行默认 Compose。API 启动时由 Flyway 创建/升级 schema，不要导入未经审核的 SQL 导出。
2. 从 [`application-local.example.yml`](blog-springboot/src/main/resources/application-local.example.yml) 或 `.env.example` 复制本地配置到未跟踪文件。不要把密码、token、密钥写进提交文件。
3. 启动 API：

   ```bash
   cd blog-springboot
   mvn spring-boot:run
   ```

4. 启动前端：

   ```bash
   cd blog-vue/blog       # 或 blog-vue/admin
   npm ci
   npm run dev
   ```

两个 Vite 应用都只使用相对 `/api`、`/uploads` 和 `/websocket` 路径；开发代理会转发到 API。

首次初始化管理员时临时设置 `BOOTSTRAP_ADMIN_ENABLED=true`、用户名和至少 12 位密码，成功后立即关闭 bootstrap。

## Compose 模式

默认栈只启动 MySQL、API、公共站点和管理后台，Redis 不在默认依赖图中：

```bash
docker compose up --build
```

需要 Redis 缓存、共享 Session、跨实例广播和 Redis Streams 时，显式叠加 Redis profile：

```bash
docker compose -f compose.yaml -f compose.redis.yaml up --build
```

Redis 仍不是文章、评论、点赞、访客、聊天历史或 Outbox 的事实源。Redis 停止后，核心读写和可恢复异步任务继续使用 MySQL/本地 fallback；多实例生产部署应启用 Redis 并使用共享 Session store。

production-like API 部署契约位于 [`deploy/backend/`](deploy/backend/)；启用 Redis 时同样叠加 `deploy/backend/compose.redis.yaml`。

## 验证

```bash
# API
cd blog-springboot
mvn -B test
mvn -B package

# 两个前端分别执行
npm run lint
npm run test:run
npm run build

# 管理后台和公共站点 bundle 检查
cd ../blog-vue/admin && npm run verify:budget
cd ../blog && npm run verify:budget
```

Docker 不可用时，Testcontainers 集成测试会明确标记 skipped；这不等同于完成 MySQL/Redis 集成验证。

## 生产交付

公共站点构建时可通过 `PRERENDER_API_URL` 获取公开文章，`PUBLIC_SITE_ORIGIN` 生成 `robots.txt`、sitemap 和 feed，并生成文章静态 HTML。浏览器仍使用相对路径，provider endpoint/credentials 不会进入 HTML 或前端状态。

反向代理必须保留 `/api`、`/uploads`、`/websocket` 规则和 WebSocket upgrade 头。监控通过 API 的 `/actuator/prometheus`，使用独立的 `X-Monitoring-Token` 请求头。

更多契约：[API-CONTRACT.md](docs/API-CONTRACT.md)、[CONFIGURATION.md](docs/CONFIGURATION.md)、[DEPENDENCY-MATRIX.md](docs/DEPENDENCY-MATRIX.md)、[REDIS-CONTRACT.md](docs/REDIS-CONTRACT.md)、[MESSAGE-RELIABILITY.md](docs/MESSAGE-RELIABILITY.md)、[MEDIA-LIFECYCLE.md](docs/MEDIA-LIFECYCLE.md)、[DEPLOYMENT-CONTRACT.md](docs/DEPLOYMENT-CONTRACT.md)、[OPERATIONS-RUNBOOK.md](docs/OPERATIONS-RUNBOOK.md)。

## 约定

- 后端遵循 `controller -> service -> dao`，DAO 接口和 MyBatis XML 必须同步。
- Entity、DTO、VO 分离；公共文章列表不得拉取 Markdown 正文。
- 新代码通过 shared API/port 使用外部能力，不在 Vue 中构造云 provider URL。
- 参见 [AGENTS.md](AGENTS.md) 获取完整贡献和安全规则。

## License

[Apache License 2.0](LICENSE)
