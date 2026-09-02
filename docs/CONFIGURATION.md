# 配置字典与部署契约

秘密值只放在环境变量或未跟踪的本地覆盖文件中。application.yml 只包含安全默认值和环境变量映射。

## 配置入口

| 层 | 文件/入口 | 责任 |
| --- | --- | --- |
| 应用 | blog-springboot/src/main/resources/application.yml | Spring 配置、默认值和变量映射 |
| 本地 API | application-local.example.yml | 本地 MySQL、邮件和 OAuth 示例 |
| 默认整合 | .env.example + compose.yaml | MySQL、API、两个前端；Redis 关闭 |
| Redis 整合 | compose.redis.yaml | 添加 Redis 并设置 APP_REDIS_ENABLED=true |
| 后端部署 | deploy/backend/*.yaml | production-like API 拓扑和持久卷 |
| 前端公开值 | blog-vue/*/.env.example | 仅 VITE_* 的公开 OAuth/captcha 值 |
| 代理 | Vite/Nginx/Vercel 配置 | /api、/uploads、/websocket 路由 |

## 关键变量

| 变量 | 默认/示例 | 作用 |
| --- | --- | --- |
| DB_URL / DB_USERNAME / DB_PASSWORD | 本地 MySQL | 数据库和 Flyway |
| APP_REDIS_ENABLED | false | Redis 能力总开关；关闭时不创建 Redis 连接工厂 |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | localhost:6379 | Redis 开启模式的连接 |
| SPRING_SESSION_STORE_TYPE | none | redis 时使用共享 Spring Session；必须同时开启 Redis |
| APP_REDIS_STREAM_* | 见 application.yml | Stream 前缀、consumer group、批量、租约和退避 |
| STORAGE_CONFIG_ENCRYPTION_KEY | 空 | 托管云存储档案的 AES-GCM 主密钥；Base64 编码 32 字节，catalog 中存在 cloud profile 时必须提供 |
| STORAGE_ACTIVE_PROVIDER | legacy bootstrap only | 旧部署一次性导入时选择 active provider；`tb_storage_bootstrap_state` 完成后不再覆盖数据库 |
| STORAGE_LOCAL_ROOT / STORAGE_LOCAL_PUBLIC_URL | 空 | 旧 local 配置的一次性 bootstrap 输入；完成后不再作为运行时事实源 |
| OSS_* | 空 | 旧阿里 OSS 配置的一次性 bootstrap 输入；完成后不再覆盖 `tb_storage_provider_config` |
| COS_* | 空 | 旧腾讯 COS 配置的一次性 bootstrap 输入；完成后不再覆盖 `tb_storage_provider_config` |
| TOS_* | 空 | 旧火山 TOS 配置的一次性 bootstrap 输入；完成后不再覆盖 `tb_storage_provider_config` |
| SEARCH_DATA_ROOT | ./data | 本地可持久化数据根目录 |
| SEARCH_INDEX_PATH | search-index | Lucene 索引目录；相对值必须位于 SEARCH_DATA_ROOT 下，绝对值也必须位于其下 |
| PAGINATION_CURSOR_SECRET | 本地开发占位值 | 游标签名；production-like 必须至少 32 字符随机秘密 |
| MONITORING_TOKEN | 空 | production-like 的 Prometheus 访问 token |
| WEBSITE_URL / API_PUBLIC_URL | 本地 URL | 公共站点、邮件和 OpenAPI origin |
| CORS_ALLOWED_ORIGINS | 两个本地前端 | 精确 CORS allowlist，不允许 * |
| OUTBOX_* | 见 application.yml | DB worker 批量、间隔、租约和最大重试 |
| BOOTSTRAP_ADMIN_* | disabled | 一次性管理员初始化 |
| PRERENDER_API_URL | 本地 API | 公共站点 SEO 构建工具读取公开文章 |
| PUBLIC_SITE_ORIGIN | 本地站点 | SEO feed/sitemap 的绝对 origin |

production、production-like、staging 会拒绝 localhost、示例域名、空监控 token 和弱游标密钥。运行时存储事实源是数据库中的托管配置档案：provider 仅 `local`、`cos`、`oss`、`tos`，同 provider 可有多档案，但 `is_active` 只能有一条。catalog 中只要存在任意 cloud profile 行，就必须提供 `STORAGE_CONFIG_ENCRYPTION_KEY`；旧 `STORAGE_*` / `OSS_*` / `COS_*` / `TOS_*` 变量只在 `legacy_import_completed = 0` 时一次性导入，完成后不会覆盖数据库。

## 托管存储档案

- 运行时由 `tb_storage_provider_config` 决定 active 档案，不再按环境变量实时切换 provider。
- 一次性导入的旧档案以 `LEGACY_ENV` source 落库，导入完成后仅数据库档案参与运行时选择。
- `local` 档案要求 `localRoot` 和 `publicUrl`；cloud 档案要求 `endpoint`、`region`、`bucket`、`publicUrl`、`accessKeyId`、`accessKeySecret`。
- 云凭据写入数据库前使用 AES-GCM 加密；API、日志和前端状态只显示“是否已配置”，不回显明文或密文。
- active 切换只影响新对象；内容和媒体资产在数据库中同时保存 `provider` 与 `storage_config_id`，历史对象继续按创建时配置读取。
- usage 由管理员手动刷新，失败时保留上次成功的对象数、字节数和最近修改时间。

## Redis 两种运行模式

- 默认模式：APP_REDIS_ENABLED=false、Session 使用普通 servlet session、缓存/限流/锁/去重使用有界本地实现，Outbox 由 DB worker 处理。
- Redis 模式：APP_REDIS_ENABLED=true；需要共享登录状态时另设 SPRING_SESSION_STORE_TYPE=redis。Redis Streams 只承担 Outbox 的可选传输，MySQL 仍是事实源。

如果 Redis 开启但运行中断，adapter 必须按能力使用安全 fallback；不得把缓存 miss 当成权限成功，也不得丢弃 PENDING/PROCESSING 的 Outbox 事件。

## 代理规则

- 浏览器请求始终使用相对 /api/...。
- /uploads/... 由 API 按当前 active local 档案提供；cloud 档案不会把 provider endpoint 暴露给浏览器。
- /websocket 必须保留 HTTP/1.1 upgrade、Upgrade 和 Connection 头。
- Vercel 后端 origin 通过 VERCEL_BACKEND_URL 注入，只接受 HTTPS、无凭据、无查询串/fragment 的 URL。

新增变量必须同步更新本文件、.env.example、应用映射和受影响的 Compose/CI/Vercel 注入。
