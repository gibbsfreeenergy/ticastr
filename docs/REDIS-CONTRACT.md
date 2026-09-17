# Redis 领域契约

Redis 是可选的加速和协调基础设施，不是业务事实源。`APP_REDIS_ENABLED=false` 是受支持的默认模式。

## 能力与故障语义

| 能力 | namespace | Redis 开启时 | 关闭/不可用时 |
| --- | --- | --- | --- |
| cache | `ticastr:cache:v1:` | cache-aside | 本地短 TTL 或直接读取 MySQL |
| 限流 | `ticastr:rate-limit:v1:` | 原子计数 + TTL | 有界本地计数；安全 endpoint 不能无条件放行 |
| 去重 | `ticastr:event:processed:v1:` | TTL 去重 | JVM 内有界去重；Outbox 仍由 MySQL 恢复 |
| authorization invalidation | `ticastr:authorization:invalidate:v1` | 发布失效通知 | 本地失效，下一次 TTL/版本检查重新加载 |
| session | `ticastr:session` | Spring Session indexed repository | 普通 servlet session |
| lock | `ticastr:lock:v1:` | ownership token + TTL | 单实例本地 token lock |
| durable transport | `ticastr:stream:v1:*` | Redis Streams consumer group | DB Outbox worker |

Redis Stream 只承载已经落库的 Outbox envelope；handler 成功后才 ACK。Redis 丢失或不可用时，MySQL Outbox 状态仍可恢复，不能把 Stream 当作文章或内容资产事实源。

## 使用规则

- key 必须包含能力 namespace 和版本；改变序列化结构时递增版本。
- port 负责 key、TTL、序列化和脚本细节，service 只传业务语义。
- cache miss、Redis exception、权限加载失败必须可区分，不能用空值伪装成功。
- 禁止通配符清空共享 Redis；清理前确认 namespace、owner 和 TTL。

实现位置：`service/*Store` 是 port，`infrastructure/redis/` 是 Redis adapter；各能力的本地 fallback 独立实现。`RedisService` 只提供核心缓存读写，不再承载互动、访客、地理位置或集合模拟接口。
