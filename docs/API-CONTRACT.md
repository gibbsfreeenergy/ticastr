# HTTP API 与实时协议契约

本文描述当前前后端共同遵守的接口边界。新增接口必须同时更新这里、对应 DTO/校验、shared domain API 以及前端验收测试。

## HTTP 响应与错误

业务接口继续使用兼容的 `Result<T>` 外形：

```json
{
  "flag": true,
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

`flag` 表示业务成功，`code` 是稳定的机器码，`message` 只用于展示/日志，`data` 是领域 DTO、列表或分页结果。HTTP status 表达协议层结果：参数错误 400、未认证 401、无权限 403、资源不存在 404、版本/状态冲突 409、限流 429、依赖不可用 503、未处理异常 500。错误响应仍保持 `Result` 结构，服务端异常不会把堆栈、SQL、provider 凭据或内部 URL 返回给浏览器。

前端通过 `blog-vue/shared/api/createApi.js` 调用 domain API。domain 方法直接返回 `Result`，不再让页面处理 Axios response 嵌套；低层 HTTP 方法只用于存量迁移。请求始终使用相对 `/api/...` 路径。

## 文章 metadata 与 Markdown 内容

文章元数据和正文是两个资源：

| 用途 | 方法 | 路径 | 返回 |
| --- | --- | --- | --- |
| 公开元数据 | GET | `/api/articles/{id}` | `ArticleDTO`，不含正文 |
| 公开正文 | GET | `/api/articles/{id}/content` | `text/markdown` 流 |
| 后台元数据 | GET | `/api/admin/articles/{id}` | 编辑 DTO，不含正文 |
| 后台正文 | GET | `/api/admin/articles/{id}/content` | `text/markdown` 流 |
| 保存正文 | PUT | `/api/admin/articles/{id}/content` | 内容资产 metadata |
| 版本列表 | GET | `/api/admin/articles/{id}/versions` | 有界 cursor page |
| 恢复版本 | POST | `/api/admin/articles/{id}/versions/{version}/restore` | 新版本 metadata |

`tb_article` 只保存 `content_asset_id` 指针；正文是对象存储中的不可变 Markdown 版本，`tb_content_asset` 保存 provider、object key、版本、大小、checksum、状态和时间。切换 provider 只影响新资产，旧资产按照自身记录的 provider 读取/删除。

保存请求示例：

```json
{
  "content": "# Hello\\n\\n正文",
  "expectedVersion": 3
}
```

`expectedVersion` 不匹配返回 HTTP 409，后台编辑器必须保留本地草稿并要求用户决定覆盖/丢弃；不能静默覆盖别人的新版本。正文响应使用 `ETag: "<sha256>"`、`Last-Modified` 和 `Cache-Control`：公开正文允许短 TTL 和 stale-while-revalidate，后台正文始终 `no-store`。客户端带条件请求命中时返回 304，且不打开对象存储流。

## 分页

### 公开列表与搜索：签名 cursor

首页、归档和搜索返回：

```json
{
  "items": [],
  "nextCursor": "签名字符串或 null",
  "hasNext": false
}
```

请求参数为 `cursor` 和 `size`，`size` 范围 1–50。cursor 由服务端签名并包含查询指纹/过期时间；客户端不能修改 offset、查询条件或排序。搜索 cursor 还必须匹配原始关键词，否则返回参数错误。

### 后台兼容分页

后台存量列表仍返回：

```json
{
  "recordList": [],
  "count": 0
}
```

`current` 从 1 开始，`size` 范围 1–100。新公开接口不要重新引入全量加载后内存分页，也不要把正文加入列表查询。

## 存储 provider 管理

管理接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/storage/provider` | 当前 provider 和可用状态，不含凭据 |
| GET | `/api/admin/storage/providers` | local/OSS/COS/TOS 的状态摘要 |
| POST | `/api/admin/storage/providers/{provider}/validate` | 有界写入/读取/删除临时对象 |
| PUT | `/api/admin/storage/provider` | 只允许激活一个已验证 provider |

后台不接收任意 object key 删除，不显示 secret/access key。provider 验证失败不能改变当前激活项；激活切换不迁移旧对象。

## Outbox 与 Redis Stream

所有可恢复异步副作用先在同一 MySQL 事务写入版本化 Outbox。Redis 关闭时由 DB worker 直接处理；Redis 开启时 bridge 把已持久化 envelope 写入按事件类型划分的 Stream，consumer group 在 handler 成功后才 ACK。Redis 不是事实源，Stream 丢失或不可用时 DB 状态仍可恢复。

事件状态为 `PENDING -> PROCESSING -> PUBLISHED`，失败进入带退避的 `PENDING` 或 `DEAD`；Redis 传输中间态为 `ENQUEUED`。投递语义是 at-least-once，handler 必须按 `eventId` 幂等。管理端只能查看有限的事件摘要/metrics，并对指定 event 执行 retry，不能任意 XADD/XDEL。

## WebSocket 聊天

每个服务端 envelope 都包含 `eventId`、`version`、`serverTime`、`type` 和 `data`。客户端发送文本时生成 `clientMessageId`，重试必须复用同一 ID；服务端以 `(client_token, client_message_id)` 唯一约束去重，并返回 `MESSAGE_ACK(type=7)`：

```json
{
  "type": 7,
  "eventId": "server-event-id",
  "version": 1,
  "serverTime": "2026-08-31T00:00:00Z",
  "clientMessageId": "client-123",
  "messageId": 42,
  "data": { "clientMessageId": "client-123", "messageId": 42 }
}
```

历史请求使用 `type=8`，`data` 只接受 `beforeId` 和有界 `limit`；响应 `type=2` 携带 `nextBeforeId`/`hasMore`。浏览器端必须去重 `eventId`/`messageId`，断线重连采用有界退避；发送失败的草稿保留在本地并可重试。录音、MediaStream、AudioContext、Object URL 和音频元素在结束/取消/卸载路径都必须释放。

## 变更验收

每次接口变更至少验证：DTO/校验和 HTTP status、认证/权限、空页、条件缓存、重复提交、409 冲突、相对代理路径，以及两个前端的 lint/test/build。新增实时消息还要验证 malformed envelope、重连、重复事件和 ACK 幂等。
