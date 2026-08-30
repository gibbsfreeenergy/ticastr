# Ticastr 单实例模块化博客架构收敛设计

## 1. 目标与范围

本文档是 2026-08-30 确认的目标设计，服务于个人博客、低并发、单实例部署场景。

目标是把当前项目收敛为一个边界清晰、数据可恢复、外部依赖可选、内容适合对象存储的模块化单体，同时完成第一、第二、第三优先级中的安全、可靠性、性能、前端体验和 SEO 改造。

本设计明确采用以下规则：

- Spring Boot API 和两个 Vue 应用继续保留。
- MySQL 是文章、互动、任务和资产引用的唯一事实来源。
- Redis 是可选的缓存、限流、幂等和短期任务传输层。
- Redis 不保存任何不可恢复的核心业务事实。
- Redis Stream 可以代替 RabbitMQ 的部分异步传输场景，但不能代替 MySQL Outbox。
- 同一时刻只激活一个对象存储 provider，支持 local、阿里 OSS、腾讯 COS、火山 TOS。
- provider 由管理后台选择，凭据由部署环境注入。
- provider 切换只影响新上传；已有资产继续使用创建时记录的 provider。
- 文章原文以 Markdown 对象保存于对象存储，MySQL 不保存文章原文。
- 不为历史数据库导出、历史 Git 数据或旧生产数据设计迁移兼容路径。
- 不将 RabbitMQ 或 Elasticsearch 作为默认运行依赖。

## 2. 总体架构

```text
公共端 / 管理端
        |
        v
Spring Boot 模块化单体
  |       |        |         |
内容域   安全域   资产域    任务/实时域
  |       |        |         |
  +-------+--------+---------+
                  |
                  v
          MySQL 事实数据层
                  |
        +---------+---------+
        |                   |
        v                   v
  Redis（可选）       COS / OSS / TOS / Local
  缓存、限流、          文章和媒体对象
  Stream、幂等
```

### 2.1 后端模块边界

- `content`：文章元数据、发布状态、内容引用、文章查询和内容读取。
- `engagement`：点赞、浏览、统计及其数据库一致性。
- `media`：图片、语音和文章内容资产生命周期。
- `security`：认证、Session principal、CSRF、CORS、限流和权限。
- `administration`：后台菜单、provider 配置、任务状态和运营查询。
- `jobs`：数据库 Outbox、Redis Stream bridge、邮件和清理任务。
- `realtime`：单实例 WebSocket 连接、聊天历史和重连协议。
- `infrastructure`：MySQL、Redis、对象存储、邮件和本地搜索索引适配器。

Controller 只负责协议转换；application service 负责编排用例；领域服务负责业务规则；DAO 和基础设施适配器不向上泄漏厂商对象。

### 2.2 前端边界

- `blog-vue/shared` 统一 HTTP、API、错误、内容读取和实时连接能力。
- 公共端只依赖公共 domain API，不拼接 provider 地址和后端 endpoint。
- 管理端只依赖管理 domain API，provider 配置页面不直接调用云 SDK。
- 继续使用 Vue Options API，按现有目录职责拆分大型组件。

## 3. 文章内容存储

### 3.1 数据模型

`tb_article` 只保存文章元数据，并通过 `content_asset_id` 指向最新内容资产；不再保存 `article_content` 原文。

逻辑上的 `tb_content_asset` 字段如下：

```text
asset_id          稳定资产 ID
article_id        所属文章
provider          local / oss / cos / tos
object_key        provider 内部对象 key
content_type      text/markdown 或 text/html
format            markdown 或 html
version           单调递增版本号
checksum          内容校验值
size              字节数
status            PENDING / ACTIVE / RETIRED / DELETED / DELETE_FAILED
created_at
updated_at
deleted_at
last_error
```

文章正文对象使用不可变 key：

```text
articles/{articleId}/{version}-{assetId}.md
```

Markdown 是编辑和恢复的规范格式。发布流程可以额外生成清洗后的 HTML 对象，但 HTML 只是派生缓存，不是编辑事实来源。

### 3.2 保存和修改

新文章：

1. 后端校验文章元数据和 Markdown。
2. 清洗危险 HTML、脚本和事件属性。
3. 生成 `assetId` 和对象 key。
4. 上传 Markdown 对象到当前 provider。
5. 在短 MySQL 事务中创建文章和内容资产引用。
6. 数据库事务失败时删除新对象，并记录清理失败。

修改文章：

1. 上传新的不可变内容对象。
2. 在短事务中切换 `tb_article.content_asset_id`。
3. 将旧内容资产标记为 `RETIRED`。
4. 提交成功后异步清理旧对象。
5. 失败时保留旧引用，删除新对象。

对象上传不包在长数据库事务中，避免网络延迟占用连接池。

### 3.3 读取

公共端和管理端通过后端内容接口读取，不直接拼接 COS、OSS 或 TOS 地址：

```text
GET /articles/{id}
GET /articles/{id}/content
GET /admin/articles/{id}
GET /admin/articles/{id}/content
```

文章元数据与正文分离返回。正文接口根据文章状态执行权限检查，再根据资产记录选择 provider。

正文响应支持 `ETag`、`Last-Modified` 和 `Cache-Control`。Redis 可用时缓存 `assetId + version`，Redis 不可用时直接读取对象存储。

### 3.4 provider 切换

后台提供：

```text
GET  /admin/storage/providers
POST /admin/storage/providers/{provider}/validate
PUT  /admin/storage/active-provider
```

激活 provider 前必须完成 endpoint、Bucket、权限和对象读写检查。激活 provider 保存在 MySQL，并由单实例进程缓存。

云厂商凭据只从环境变量或部署密钥读取，后台只能选择和检查 provider，不能保存明文凭据。

provider 适配器统一实现：

- `put`：流式上传对象；
- `get`：流式读取对象；
- `head`：查询对象元数据；
- `delete`：删除对象；
- `publicUrl` 或受控内容读取；
- 连接超时、读取超时、有限重试和客户端关闭。

所有对象 key 由服务端生成，客户端不能传入任意 key。

## 4. MySQL、Redis 与异步任务

### 4.1 MySQL 事实数据

- 文章点赞使用 `user_id + article_id` 唯一约束。
- 浏览量和点赞量使用数据库原子更新。
- Redis 只用于短时间去重、缓存和热点读取。
- 评论、留言、文章状态和媒体引用不依赖 Redis 才能存在。
- Redis 清空后，系统可以继续运行并从 MySQL 恢复数据。

核心缓存 key 使用版本化命名：

```text
article:metadata:{articleId}:{updatedAt}
article:content:{assetId}:{version}
home:summary
search:result:{queryHash}
```

### 4.2 Redis 可选模式

Redis 由显式开关控制：

- Redis 开启：启用缓存、Redis 原子限流、幂等 key 和 Redis Stream。
- Redis 关闭：不创建 Redis Session、Redis listener 或 Redis provider；缓存失效后直接访问 MySQL/对象存储；限流使用有界进程内实现；Outbox 由数据库任务直接处理。

Redis 故障不能导致文章发布、文章读取、评论保存和 provider 配置不可用。

### 4.3 Outbox 与 Redis Stream

业务事务先写 MySQL Outbox：

```text
PENDING -> ENQUEUED -> PROCESSING -> PUBLISHED
                         |
                         +-> PENDING（可重试）
                         +-> DEAD（超过最大次数）
```

Redis 可用时，bridge 将 Outbox envelope 写入 Redis Stream；Consumer Group 负责读取、处理和 ACK。Redis 不可用时，数据库 worker 直接执行同一组 handler。

每个事件包含：

```text
eventId
eventType
version
aggregateId
occurredAt
traceId
payload
```

所有 handler 以 `eventId` 实现幂等。必须支持 Pending 消息转移、超时恢复、死信查看、人工重试和处理指标。

Redis Stream 只用于邮件、媒体清理、通知等异步任务，不用于文章事实数据、聊天历史或单实例 WebSocket 广播。

RabbitMQ 和 Elasticsearch 不属于默认运行拓扑。正文搜索使用本地可重建索引；邮件使用 Outbox handler。

## 5. 安全边界

### 5.1 限流与配额

登录、注册、验证码、评论、留言、点赞、`/voice`、`/report` 和 provider 连通性检查都必须限流。

限流 key 按接口、IP、登录用户 ID 或匿名 client token 组合。Redis 不可用时使用带最大条目数和过期时间的进程内实现，不能无限增长。

语音和媒体上传另设单 IP、单用户和匿名 token 配额。

### 5.2 文件和内容校验

- 图片校验大小、像素、真实 MIME 和解码结果。
- 音频校验 WAV 结构、编码、采样率、时长和大小。
- Markdown 校验大小并在服务端清洗危险 HTML。
- 对象 key 使用随机 ID，禁止使用原始文件名和用户输入路径。
- 文章内容、图片和语音均记录 checksum、size、content type 和生命周期状态。

### 5.3 认证与浏览器安全

- Session principal 只包含用户 ID、用户名、角色、状态和必要展示字段。
- 密码只用于认证过程，不进入 Session principal。
- 生产环境强制 Secure Cookie，并明确 SameSite 策略。
- 状态修改接口统一使用 CSRF 保护。
- 严格校验 CORS origin。
- 添加 CSP、HSTS、X-Content-Type-Options、Frame 防护等响应头。
- 外部验证码脚本使用明确白名单；静态图标依赖优先本地打包。

## 6. 搜索、缓存与性能

### 6.1 正文搜索

发布或修改文章后，内容服务异步更新本地嵌入式搜索索引。索引目录使用持久化卷，例如：

```text
data/search-index/
```

索引保存文章 ID、标题、分类、标签和正文检索字段，不作为文章原文来源。索引损坏或删除后，系统从对象存储重新构建。

搜索接口不再对 MySQL `longtext` 执行 `%keyword%` 查询。索引更新失败不阻塞文章发布，但会进入 Outbox 重试流程。

### 6.2 查询路径

- 公共文章列表使用基于 ID/时间的游标分页。
- 文章详情先加载元数据，再读取缓存或对象存储正文。
- 推荐文章和最新文章使用缓存或异步加载，不在无效文章 ID 检查前启动查询。
- 管理端文章列表只批量读取当前页的统计数据。
- 首页聚合信息使用短 TTL 缓存。
- 后台统计按模块拆分，避免一次请求加载所有曲线和列表。
- 在线用户查询使用独立索引和分页，不遍历全部账号后逐个查询 Session。
- 媒体引用检查改为资产索引和对账任务，删除请求不执行多表串行扫描。

### 6.3 媒体性能

- 图片保存原图和展示缩略图。
- 公共端使用 WebP/AVIF、响应式尺寸和懒加载。
- 内容和媒体读取支持 ETag 和 CDN/浏览器缓存。
- 不在 API 中无条件读取完整 Redis Hash/ZSet。

### 6.4 性能验收目标

单实例基线目标：

- 文章列表 p95 < 200ms；
- Redis 命中时文章详情 p95 < 150ms；
- 对象存储缓存未命中时文章详情 p95 < 500ms；
- 搜索 p95 < 300ms；
- 公共端首屏 LCP < 2.5s；
- Redis 关闭时核心业务仍可用。

以上目标必须通过真实 MySQL、对象存储模拟器或受控 provider 环境以及压测数据验证。

## 7. 前端、聊天和 SEO

### 7.1 文章页面

公共端文章页面拆分为文章元信息、正文、目录、评论、推荐和上下篇导航组件。先显示元数据和骨架屏，再加载正文、评论和推荐。

管理端编辑器读取和提交 Markdown；草稿、预览、自动保存、发布和版本恢复使用内容资产版本模型。

### 7.2 聊天室

单实例使用本地连接注册表和数据库历史：

- 自动重连和指数退避；
- 连接状态提示；
- `eventId` 去重；
- 发送失败提示和重试；
- 历史消息分页；
- 录音资源统一释放；
- 语音上传失败可见；
- 消息撤回和历史加载不重复追加。

聊天历史不通过 Redis Stream 作为唯一来源。

### 7.3 页面拆分

继续使用 Options API，拆分当前大型文件：

- 公共端 `Article.vue`：正文、目录、评论、推荐和文章加载；
- 公共端 `ChatRoom.vue`：连接、消息列表、输入框和录音；
- 管理端 `NavBar.vue`：菜单树、用户菜单和导航状态；
- 管理端 `Home.vue`：统计卡片、图表和数据加载。

### 7.4 SEO

公共端构建阶段预渲染公开文章页面，并生成：

- 独立 title、description 和 canonical；
- Open Graph 与 Twitter Card；
- Article JSON-LD；
- sitemap.xml；
- robots.txt；
- RSS/Atom。

预渲染通过内容 API 获取正文，不把文章原文重新写入 MySQL。

## 8. 测试与交付门槛

### 8.1 单元测试

必须覆盖：

- Markdown 清洗和大小限制；
- 图片、音频真实格式和时长校验；
- provider 选择和切换；
- 内容资产状态流转；
- 上传成功但数据库失败时的补偿删除；
- 旧内容引用不会被新版本覆盖；
- Redis 开启和关闭两条路径；
- Redis Stream ACK、Pending 恢复和幂等；
- 搜索索引更新和重建；
- 无密码 Session principal；
- 限流和匿名配额。

### 8.2 集成测试

必须使用真实 MySQL 验证 Flyway 全新建库、文章引用、点赞唯一约束和 Outbox。Redis 测试分为启用和禁用两套 profile。对象存储 provider 使用统一合同测试，真实 COS/OSS/TOS 连通性测试仅在受控凭据环境中执行。

### 8.3 浏览器验收

公共端验证首页、文章列表、正文读取、搜索、评论、留言、媒体、断网恢复和预渲染页面。管理端验证登录、草稿、自动保存、预览、发布、版本恢复、provider 检查、provider 切换、上传、删除和失败重试。

### 8.4 CI 门禁

CI 必须执行：

- 后端 clean test 和 package；
- MySQL 集成测试；
- Redis 开启/关闭测试；
- 两个前端 lint、unit test 和 build；
- 公共端和管理端 bundle budget；
- 架构、代理和 Compose 契约检查；
- 浏览器核心流程冒烟；
- 性能基线和安全扫描。

没有 Docker 时，CI 不得把被跳过的集成测试报告为完整通过。

## 9. 实施顺序

### 第一优先级：安全与核心一致性

完成安全响应头、CSRF/CORS、限流、上传校验、无凭据 principal、provider 配置检查、文章内容资产模型、MySQL 统计事实来源和 API 契约测试。

### 第二优先级：可靠任务与性能

完成 Redis 可选开关、MySQL Outbox、Redis Stream bridge、应用内 fallback worker、本地正文搜索索引、文章详情缓存、游标分页、后台统计优化、媒体对账和 provider adapter 合同测试。

### 第三优先级：前端与内容体验

完成文章内容接口拆分、文章版本和自动保存、聊天室重连、组件拆分、图片优化、预渲染、SEO 元信息、站点地图、RSS 和浏览器性能门禁。

## 10. 完成定义

以下条件全部满足后，本设计才视为完成：

1. 默认单实例不需要 RabbitMQ 或 Elasticsearch 即可启动并提供完整核心功能。
2. Redis 关闭时，文章、评论、留言、登录、发布和内容读取仍然可用。
3. MySQL 不保存文章正文，文章内容可以通过资产引用稳定读取。
4. COS、OSS、TOS 至少各有一个通过统一合同测试的 provider 实现。
5. 管理后台可检查并切换激活 provider，切换不影响旧资产。
6. 内容更新失败不会破坏旧版本，孤儿对象和删除失败对象可以对账和重试。
7. 正文搜索不依赖 MySQL `LIKE`，索引可以从对象存储重建。
8. 文章、语音、评论、留言和 provider 管理接口具备限流、权限和错误回归测试。
9. 公共端和管理端核心流程通过浏览器冒烟，公共页面具备预渲染和 SEO 基础能力。
10. 集成测试、构建、架构检查和性能基线均在 CI 中有明确结果。
