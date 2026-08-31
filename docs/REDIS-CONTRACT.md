# Redis 领域契约

Redis 是可选的加速/协作基础设施，不是业务事实源。APP_REDIS_ENABLED=false 是受支持的默认运行模式。

## 能力与故障语义

| 能力 | namespace | Redis 开启时 | 关闭/不可用时 |
| --- | --- | --- | --- |
| 限流 | ticastr:rate-limit:v1: | Lua/原子计数 + TTL | 有界本地计数；安全敏感 endpoint 不得无条件放行 |
| cache | ticastr:cache:v1: | cache-aside | 本地短 TTL 或直接读 MySQL |
| 去重 | ticastr:event:processed:v1: | claim/complete/release + TTL | JVM 内有界去重；Outbox 仍以 MySQL 状态恢复 |
| presence | ticastr:chat:online-sessions:v1 | TTL heartbeat | 本实例连接 registry |
| chat broadcast | ticastr:chat:events:v1 | Pub/Sub best-effort | 本实例广播 |
| authorization invalidation | ticastr:authorization:invalidate:v1 | Pub/Sub 通知 | 本地失效；下一次 TTL/版本检查重新加载 |
| session | ticastr:session | Spring Session indexed repository | 普通 servlet session |
| lock | ticastr:lock:v1: | ownership token + TTL | 本地 token lock，单实例有效 |
| durable transport | ticastr:stream:v1:* | Redis Streams consumer group | DB Outbox worker |

Stream key 使用 app.redis.streams.prefix 加事件类型，group 使用配置的 consumer-group。事件仅在 DB row 已存在后发布；handler 成功后才 ACK，失败会保留 pending 或进入 dead-letter stream，同时 DB row 可恢复。

## 使用规则

- key 必须包含能力 namespace 和版本；改变序列化结构时递增版本。
- port 负责 key、TTL、序列化和脚本细节；service 只传业务语义。
- Pub/Sub 不能承担重放、审计或唯一投递；需要恢复的事件必须先写 Outbox。
- cache miss、Redis exception、权限加载失败必须可区分，不能用空值伪装成功。
- 禁止通配符清空共享 Redis；清理前确认 namespace、owner 和 TTL。

实现位置：service/*Store 是 port，infrastructure/redis/ 是 Redis adapter，InMemoryRedisService 和 local stores 是单实例 fallback。遗留 RedisService 只用于尚未迁移的兼容代码，新能力不得扩大其使用范围。

关闭 Redis 时，兼容 fallback 也有硬上限：最多保留 2048 个 key，每个 Hash/Set/List/ZSet/Geo/HyperLogLog 最多 2048 个成员，Bitmap 偏移量最多 8 MiB。超出集合上限会淘汰最旧成员，超出 key 上限会淘汰最久未访问 key；这些数据都不是事实源，淘汰后必须从 MySQL 或安全默认值恢复。
