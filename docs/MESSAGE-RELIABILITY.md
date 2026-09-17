# Outbox 可靠性契约

本文只描述核心博客仍使用的可恢复异步事件：文章搜索索引和媒体删除。它们都先写入 MySQL Outbox，再由 DB worker 处理；Redis Streams 只是可选传输层。

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

`eventId` 是幂等主键，`eventType`/`version` 决定 payload 解释，`traceId` 用于 worker 日志关联。handler 必须校验 envelope 和 payload，不把展示文本当作协议字段。

## 状态机

```text
PENDING -> PROCESSING -> PUBLISHED
              |
              +-> PENDING/DEAD
```

失败按上限和指数退避回到 `PENDING`；不可恢复或超过最大次数进入 `DEAD`。领取使用 `FOR UPDATE SKIP LOCKED`，过期的 `PROCESSING` 可以重新领取。Redis Streams handler 成功后才 ACK，Redis 异常时 DB worker 直接处理同一事件。

## 运维信号

监控 Outbox pending/processing/dead、最老事件年龄、handler 时延、重试次数、Stream pending/dead、媒体删除失败和搜索索引状态。Redis Stream 不是唯一来源，任何时候都可以从 MySQL Outbox 恢复；禁止通过删除 Outbox 行来绕过失败。
