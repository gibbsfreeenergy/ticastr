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

`tb_article` 只保存 `content_asset_id` 指针；正文是对象存储中的不可变 Markdown 版本，`tb_content_asset` 保存 provider、`storage_config_id`、object key、版本、大小、checksum、状态和时间。切换 active 存储配置只影响新资产，旧资产优先按自身记录的 `storage_config_id` 读取/删除；只有历史 `NULL` 行才保留 provider 类型兼容回退。

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

搜索结果返回前按数据库的当前公开状态过滤，并继续读取候选结果补齐页面；索引内部批次上限不会截断 `size=20` 或 `size=50` 的下一页判断。公共搜索框使用 `nextCursor` 加载更多，关键词变化时清空旧结果与游标。

### 后台兼容分页

后台存量列表仍返回：

```json
{
  "recordList": [],
  "count": 0
}
```

`current` 从 1 开始，`size` 范围 1–100。新公开接口不要重新引入全量加载后内存分页，也不要把正文加入列表查询。

## 权限变更与会话

修改用户角色或禁用用户后，在数据库事务提交成功时撤销该用户现有会话；事务回滚不撤销会话。用户需要重新登录以获取新权限。角色与资源权限缓存的失效及跨节点广播同样在事务提交后执行，避免并发请求缓存未提交的旧配置。

## 托管存储配置管理

运行时存储目录由数据库中的 `tb_storage_provider_config` 管理。provider 只允许 `local`、`cos`、`oss`、`tos`；同一 provider 可以保存多条档案，但 `is_active` 在数据库层只允许一条 active 记录负责新对象。所有列表、创建、更新、验证、激活和 usage 响应都只返回安全摘要；摘要允许包含已校验的 `endpoint`、`region`、`bucket`、`localRoot`、`publicUrl`，但不返回明文凭据、AES-GCM 密文、签名、Authorization、任意 object key 或供应商内部请求 URL。

### 主接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/storage/configs` | 返回 `StorageConfigListResponse`，包含 `activeConfigId` 和全部配置摘要 |
| POST | `/api/admin/storage/configs` | 创建 inactive 配置档案 |
| PUT | `/api/admin/storage/configs/{id}` | 更新指定配置；更新时空凭据表示保留原密文 |
| DELETE | `/api/admin/storage/configs/{id}` | 删除 inactive 且未被内容/媒体资产引用的配置 |
| POST | `/api/admin/storage/configs/{id}/validate` | 对指定配置执行有界写入/读取/删除验证，并持久化安全验证快照 |
| POST | `/api/admin/storage/configs/{id}/activate` | 先验证，再原子切换唯一 active 配置 |
| POST | `/api/admin/storage/configs/{id}/usage` | 手动刷新真实用量快照 |

请求体字段固定为 `name`、`provider`、`endpoint`、`region`、`bucket`、`localRoot`、`publicUrl`、`accessKeyId`、`accessKeySecret`，字段规则如下：

| 字段 | `local` | `cos` / `oss` / `tos` |
| --- | --- | --- |
| `name` | 必填 | 必填 |
| `provider` | 必须为 `local` | 必须为 `cos`、`oss` 或 `tos` |
| `endpoint` | 不使用；请求值被忽略并清除 | 必填，且必须是无凭据、无 query、无 fragment 的绝对 HTTP(S) URL |
| `region` | 不使用；请求值被忽略并清除 | 必填 |
| `bucket` | 不使用；请求值被忽略并清除 | 必填 |
| `localRoot` | 必填，且必须是规范化后的绝对路径 | 不使用；请求值被忽略并清除 |
| `publicUrl` | 必填，且必须是安全的 HTTP(S) 绝对 URL | 必填，且必须是安全的 HTTP(S) 绝对 URL |
| `accessKeyId` | 不使用；请求值被忽略并清除 | 创建时必填；更新时留空表示保留原值 |
| `accessKeySecret` | 不使用；请求值被忽略并清除 | 创建时必填；更新时留空表示保留原值 |

云配置的凭据以 `STORAGE_CONFIG_ENCRYPTION_KEY` 提供的 Base64 32 字节 AES 主密钥做 AES-GCM 加密后落库。响应 DTO 只暴露：

- 配置摘要：`id`、`name`、`provider`、`active`、`configured`、`credentialsConfigured`、`endpoint`、`region`、`bucket`、`localRoot`、`publicUrl`
- 验证快照：`status`、`success`、`validatedAt`、`message`
- 用量快照：`status`、`objectCount`、`bytes`、`latestModified`、`checkedAt`、`error`

`validate` 成功时返回 `status=SUCCESS`、`success=true`、`message=验证成功`；字段不完整或真实 provider 校验失败时仍返回 200，但 `status=FAILED`，消息只允许 `配置字段不完整` 或 `配置验证失败`。`activate` 失败时返回 409，并保持原 active 配置不变。`usage` 始终由管理员手动触发，统计真实 regular file 或云对象的数量、字节数和最近修改时间；统计失败时返回 `status=FAILED`、`error=使用量刷新失败`，同时保留上次成功的 `objectCount`、`bytes` 和 `latestModified`。

这组接口的错误语义补充如下：

- `POST` / `PUT` 字段缺失、provider 不支持、URL 不合法或 `localRoot` 不是绝对规范化路径时返回 400。
- 任一带 `{id}` 的路由在配置不存在时返回 404。
- `DELETE` active / referenced 配置、`POST /activate` 的预验证失败，以及旧 provider-only 路由无法唯一映射配置时返回 409。
- `POST /validate` 和 `POST /usage` 的真实 provider 故障不会回传 SDK 细节，而是返回 200 + 安全失败快照。

删除规则：

- active 配置不能删除，返回 409。
- 仍被 `tb_content_asset` 或 `tb_media_asset` 引用的配置不能删除，返回 409。
- 不存在的配置 ID 返回 404。

### 兼容路由

为滚动发布保留旧 provider 路由：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/storage/provider` | 返回当前 active provider、`activeConfigId` 和支持的 provider 列表 |
| GET | `/api/admin/storage/providers` | 返回四类 provider 的聚合状态摘要和可用档案数 |
| POST | `/api/admin/storage/providers/{provider}/validate` | 仅当该 provider 恰好只有一条档案时执行兼容验证 |
| PUT | `/api/admin/storage/provider` | 仅当目标 provider 恰好只有一条档案时切换到该配置 |

当同一 provider 存在多条档案时，旧 `/provider` 切换和 `/providers/{provider}/validate` 无法唯一定位配置，返回 409，并要求调用方改用配置 ID 路由。这个歧义包含同 provider 的未完成草稿档案；新管理页面不再依赖旧接口。

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
