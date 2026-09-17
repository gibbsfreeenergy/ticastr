# 基础设施依赖矩阵

MySQL 是唯一持久事实源。业务层依赖 port/application service，云 SDK 和 Redis client 只出现在适配器或基础设施配置中。

| 能力 | 入口 | 实现 | 启用条件 | 事实与失败边界 |
| --- | --- | --- | --- | --- |
| MySQL/Flyway | DAO、迁移目录 | MyBatis-Plus/MySQL | 始终 | schema、文章元数据、内容指针和 Outbox；不可用时 readiness 失败 |
| Redis cache | `CacheStore` | Redis adapter / 本地缓存 | `APP_REDIS_ENABLED=true` | 非事实数据；故障按缓存未命中处理 |
| Redis 限流/锁/去重 | `RateLimitStore`、`LockStore`、`EventDeduplicationStore` | Redis adapter / 有界本地实现 | Redis 可选 | 只做加速和协调；安全敏感操作不因缓存故障放行 |
| Spring Session | servlet session | Redis indexed repository 或本地 session | 显式开启 Redis session | 多实例共享状态只在 Redis session 模式保证 |
| Durable events | Outbox handler | MySQL Outbox + DB worker，Redis Streams 可选 | Outbox 始终 | 搜索索引和媒体删除事件可恢复、幂等 |
| Article search | `ArticleSearchApplicationService` | 本地 Lucene | 始终 | 索引可删除重建；正文不回写 MySQL |
| Media/content storage | `MediaAssetStore`、`StorageProvider` | local、Aliyun OSS、Tencent COS、Volcengine TOS | 托管 profile catalog | 资产保存 provider 和配置 ID；删除前检查核心引用 |

## 依赖治理

1. 不在 controller/service/DAO 直接 import 云 SDK 或 `RedisTemplate`。
2. Redis 是否启用由 `app.redis.enabled` 决定，而不是由 classpath 决定；关闭时不创建 Redis 连接工厂、Session repository 或 Stream consumer。
3. 新增存储 provider 必须实现 `StorageProvider`，object key 由服务端生成，配置摘要不得返回凭据、密文、签名或任意 object key。
4. 修改依赖前执行 `mvn -B dependency:tree`，同步检查许可证、漏洞和镜像体积。
