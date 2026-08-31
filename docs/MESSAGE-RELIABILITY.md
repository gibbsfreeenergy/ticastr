# 消息可靠性与事务事件契约

实时聊天广播和可恢复业务事件分开处理：聊天广播可丢，邮件、搜索索引、媒体清理和内容清理必须可恢复。

## Durable envelope

```json
{
  "eventId": "uuid",
  "eventType": "ARTICLE_CONTENT_INDEX",
  "version": 1,
  "occurredAt": "2026-08-30T00:00:00Z",
  "aggregateId": "article-42",
  "traceId": "uuid",
  "payload": {}
}
```

eventId 是幂等主键，eventType/version 决定 payload 解释，traceId 用于跨请求/worker 日志关联。handler 必须校验 envelope 和 payload，不得把展示文本当协议字段。

## 状态机

PENDING -> PROCESSING -> PUBLISHED

失败按上限和指数退避回到 PENDING；不可恢复或超过最大次数进入 DEAD，保留截断的 last_error。领取使用 FOR UPDATE SKIP LOCKED，过期 PROCESSING 可重新领取。默认批量 20、租约 10 分钟、最大 8 次、退避上限 300 秒。

Redis 开启时，bridge 将已领取的 envelope 放入 Streams，consumer group 在 handler 成功后 ACK；Redis 关闭或异常时 DB worker 直接调用同一 handler。Redis Stream 不是唯一来源，任何时候都可以通过 MySQL Outbox 重放。

## 分类

| 类型 | 示例 | 是否允许丢失 | 恢复 |
| --- | --- | --- | --- |
| best-effort | 聊天广播、presence、缓存失效 | 是 | 历史查询/下一次状态刷新 |
| durable | 邮件、文章索引、媒体/内容删除 | 否 | Outbox、Stream pending、重试、死信、人工 retry |
| audit | 安全/操作日志 | 原则上否 | MySQL 保留策略和监控告警 |

## 运维信号

至少监控 Outbox pending/processing/dead、最老事件年龄、handler 时延、重试次数、Stream pending/dead、Redis bridge 错误、provider 删除失败和 search index 状态。后台只提供查看和指定 event retry，不允许任意发布或删除事件。
