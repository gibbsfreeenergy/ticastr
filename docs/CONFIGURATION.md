# 配置字典与部署契约

秘密值只放在环境变量或未跟踪的本地覆盖文件中。`application.yml` 只包含安全默认值和环境变量映射。

## 配置入口

| 层 | 文件/入口 | 责任 |
| --- | --- | --- |
| 应用 | `blog-springboot/src/main/resources/application.yml` | Spring 配置、默认值和变量映射 |
| 本地 API | `application-local.example.yml` | 本地 MySQL、存储和管理员示例 |
| 默认整合 | `.env.example` + `compose.yaml` | MySQL、API、两个前端；Redis 关闭 |
| Redis 整合 | `compose.redis.yaml` | 增加 Redis 并设置 `APP_REDIS_ENABLED=true` |
| 后端部署 | `deploy/backend/*.yaml` | production-like API 拓扑和持久卷 |
| 前端公开值 | `blog-vue/*/.env.example` | 仅站点构建 origin 等公开变量；管理员验证码仅供后台登录 |
| 代理 | Vite/Nginx/Vercel 配置 | `/api`、`/uploads` 路由 |

## 关键变量

| 变量 | 默认/示例 | 作用 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地 MySQL | 数据库和 Flyway |
| `APP_REDIS_ENABLED` | `false` | Redis 能力总开关 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost:6379` | Redis 开启模式的连接 |
| `SPRING_SESSION_STORE_TYPE` | `none` | 显式启用共享 Redis Session |
| `APP_REDIS_STREAM_*` | 见 `application.yml` | Outbox Stream 传输参数 |
| `STORAGE_CONFIG_ENCRYPTION_KEY` | 空 | 云存储凭据的 AES-GCM 主密钥 |
| `STORAGE_*` / `OSS_*` / `COS_*` / `TOS_*` | 空 | bootstrap 未完成时的一次性存储导入输入 |
| `SEARCH_DATA_ROOT` / `SEARCH_INDEX_PATH` | `./data` / `search-index` | 可重建的 Lucene 索引目录 |
| `PAGINATION_CURSOR_SECRET` | 本地占位值 | 公开列表/搜索 cursor 签名；生产环境必须使用强随机值 |
| `MONITORING_TOKEN` | 空 | production-like 的 Prometheus 访问 token |
| `WEBSITE_URL` / `API_PUBLIC_URL` | 本地 URL | 站点 SEO 和 API origin |
| `CORS_ALLOWED_ORIGINS` | 两个本地前端 | 精确 CORS allowlist |
| `OUTBOX_*` | 见 `application.yml` | DB worker 的批量、租约和重试参数 |
| `BOOTSTRAP_ADMIN_*` | disabled | 一次性管理员初始化 |
| `PRERENDER_API_URL` / `PUBLIC_SITE_ORIGIN` | 本地地址 | 公共站点 SEO 构建 |
| `VITE_TENCENT_CAPTCHA_ID` | 空 | 管理端登录验证码 |

production、production-like、staging 会拒绝 localhost、示例域名、空监控 token 和弱 cursor secret。运行时存储事实源是 `tb_storage_provider_config`；旧环境变量只在 `legacy_import_completed = 0` 时导入一次。

## 托管存储档案

- 运行时由数据库中的 `tb_storage_provider_config` 选择 active 档案。
- `local` 要求 `localRoot` 和 `publicUrl`；云档案要求 endpoint、region、bucket、publicUrl 和凭据。
- 云凭据写入数据库前使用 AES-GCM 加密；API、日志和前端只显示安全摘要。
- active 切换只影响新对象；文章内容和媒体资产按自身保存的 `storage_config_id` 读取。
- usage 由管理员手动刷新，失败时保留上次成功快照。

## Redis 两种运行模式

- 默认模式：普通 servlet session、本地缓存/限流/锁/去重、Outbox 由 DB worker 处理。
- Redis 模式：Redis adapter 提供缓存、协调、共享 Session 和可选 Stream 传输；MySQL 仍是事实源。

## 代理规则

- 浏览器请求始终使用相对 `/api/...`。
- `/uploads/...` 由 API 或配置的公开存储路径提供。
- Vercel 后端 origin 通过 `VERCEL_BACKEND_URL` 注入，只接受安全的 HTTPS URL。

新增变量必须同步更新本文件、`.env.example`、应用映射和受影响的 Compose/CI/Vercel 注入。
