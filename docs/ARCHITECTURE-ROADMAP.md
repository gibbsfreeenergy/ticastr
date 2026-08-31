# Ticastr 架构现状与后续路线

## 当前目标架构

Ticastr 采用单实例优先、可平滑扩展到多实例的模块化单体。核心边界是：

```text
Vue public/admin
        |
  relative HTTP/WebSocket
        |
Spring controller -> application service -> DAO/port
        |                    |
      MySQL              object storage
        |
  transactional outbox -> DB worker
                         \-> optional Redis Streams
```

MySQL 是唯一持久事实源。Redis 只提供可选 cache、限流、锁、Session、presence、Pub/Sub 和 Streams 传输；Redis 关闭或故障时必须有有界 fallback。文章正文不再作为 tb_article.article_content 存储，而是对象存储中的不可变 Markdown 版本，MySQL 只存指针和元数据。搜索使用可删除重建的本地 Lucene 索引。

## 已完成的优先级改造

### 第一优先级：数据、安全、内容

- Flyway 迁移拆出内容资产、互动事实、provider 配置、Outbox、访客事实和查询索引。
- 文章 metadata/content 分离，内容有版本、checksum、ETag、expectedVersion 冲突保护和恢复流程。
- local/OSS/COS/TOS 统一 provider port，后台验证后一次激活一个 provider，旧资产保留原 provider。
- 上传校验真实媒体结构、大小、尺寸/像素和 Markdown HTML；session principal 不保存密码。
- CORS、CSP、CSRF、限流、监控 token、错误脱敏和审计边界统一。

### 第二优先级：可靠性、Redis、搜索、性能

- RabbitMQ/Elasticsearch 不属于默认运行时；MySQL Outbox + handler 是异步基础。
- Redis 可选；Redis Streams 仅作 Outbox 传输，ACK-after-handler，DB worker 可回退。
- 公共文章/归档/搜索使用签名 cursor；正文搜索通过 Lucene，索引可重建。
- 点赞、访客、排名等事实回到 MySQL；缓存只是 cache-aside 加速。
- 媒体/内容删除异步化并按页对账、引用保护、失败重试。

### 第三优先级：前端、实时、交付

- 公共文章 metadata、Markdown、评论和推荐按职责/状态独立加载；后台编辑器支持 Markdown 版本、自动保存、预览、发布和恢复。
- shared HTTP 做安全错误归一化、content validator cache；WebSocket 做 bounded reconnect、事件去重和资源释放。
- 管理后台可查看 provider 状态、验证/切换和 Outbox；不显示凭据或任意删除能力。
- 公共站点构建生成文章 prerender、canonical/OG/Twitter/JSON-LD、sitemap、robots、feed 和响应式图片。
- CI 统一执行 backend、frontend、架构/代理/Compose 契约、bundle、浏览器 smoke 和性能/安全检查。

## 不做的过度设计

- 不拆成微服务，不为单实例引入服务发现、分布式事务或复杂编排。
- 不同时维护 RabbitMQ、Elasticsearch、Redis Streams 三套 durable 业务语义。
- 不把 Redis、浏览器本地缓存或 Lucene 当作事实源；所有都可清空重建。
- 不让 Vue 直接连接 COS/OSS/TOS，也不让前端拼 provider URL。
- 不用“全量加载后在内存分页”、SELECT *、N 次计数查询或无限重连掩盖性能问题。

## 后续路线

1. 用带 MySQL/Redis 的真实环境完成 Testcontainers/Compose matrix，并记录跳过原因，而不是把 unit pass 当成集成通过。
2. 使用至少 10,000 条文章 metadata 和 100,000 条互动/Outbox 数据执行 p50/p95 基准；没有实测前不宣称性能提升。
3. 若未来需要多实例，先启用 Redis Session/Streams 并验证 idempotency、pending recovery、WebSocket 广播和锁，再考虑独立 worker 部署。
4. 当搜索规模超过本地 Lucene 的单机容量，先保留 ArticleSearchApplicationService port，再评估托管搜索，不把 provider 选择泄露到业务层。

## 完成标准

功能完成必须同时满足：核心数据库迁移可从 legacy baseline 升级；Redis off/on/failure 三种模式有证据；正文不会回到 MySQL；Outbox 可恢复；provider 删除有引用保护；前端 lint/test/build/budget 通过；部署契约不启动被移除的中间件；所有跳过的真实依赖测试在报告中可见。
