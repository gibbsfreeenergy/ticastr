# 基础设施依赖矩阵

MySQL 是唯一持久事实源。业务层依赖 port/application service，云 SDK、Redis client 和邮件客户端只能出现在 adapter 或基础设施配置中。

| 能力 | port/入口 | adapter | 启用条件 | 事实与失败边界 |
| --- | --- | --- | --- | --- |
| MySQL/Flyway | DAO、迁移目录 | MyBatis-Plus/MySQL | 始终 | schema、文章元数据/内容指针、互动事实和 Outbox；不可用则 readiness 失败 |
| Redis cache/限流/锁/去重 | CacheStore、RateLimitStore、LockStore、EventDeduplicationStore | Redis adapters + local bounded fallback | APP_REDIS_ENABLED=true | 非事实数据；Redis 故障不得绕过权限/限流，核心路径回退或安全拒绝 |
| Spring Session | servlet session | Redis indexed repository 或本地容器 session | Redis session 显式配置 | 多实例共享状态只在 Redis session 模式保证 |
| chat presence/broadcast | ChatPresenceStore、ChatEventPublisher | Redis Pub/Sub 或本地 registry | Redis 开启时跨实例 | 聊天历史在 MySQL；广播 best-effort，断线可恢复 |
| durable events | DurableEventPublisher、Outbox handler | MySQL Outbox + DB worker，Redis Streams bridge 可选 | Outbox 始终；Streams 可选 | PENDING/PROCESSING/PUBLISHED/DEAD，ACK/完成后才确认 |
| article search | ArticleSearchApplicationService | 本地 Lucene | 始终 | 索引可删除重建；正文不回写 MySQL |
| media/content storage | MediaAssetStore、StorageProvider | local、Aliyun OSS、Tencent COS、Volcengine TOS | 一个 active provider | 资产记录 provider/object key；删除异步且引用安全 |
| SMTP | Outbox handler | JavaMailSender | 邮件 handler 执行时 | 不在请求线程发送；失败重试/dead |
| QQ/微博 OAuth | SocialLoginStrategy | provider strategy | 用户触发 | RestTemplate 有超时；外部响应不直接进入 HTML |

## 依赖治理

1. 不在 controller/service/DAO 直接 import 云 SDK、RedisTemplate、邮件 client 或 RestTemplate。
2. 外部调用必须有超时、有限重试、可观测错误和关闭语义。
3. Redis 是否可用由 app.redis.enabled 决定，而不是由 classpath 决定；关闭时不创建连接工厂、Redis Session repository、listener 或 Stream consumer。
4. 新增 provider 必须实现 StorageProvider，object key 由服务端生成，后台状态 API 不得返回 endpoint secret。
5. 更新依赖前执行 mvn -B dependency:tree，同步检查许可证、漏洞和镜像体积。
