# HTTP API 契约

本文描述当前前后端共同遵守的核心接口边界。请求始终使用相对 `/api/...` 路径，前端通过 `blog-vue/shared/api/createApi.js` 调用 domain API。

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

`flag` 表示业务成功，`code` 是稳定机器码，`message` 只用于展示，`data` 是领域 DTO、列表或分页结果。参数错误、未认证、无权限、资源不存在、版本冲突、限流和依赖故障分别使用对应 HTTP status；服务端异常不得返回堆栈、SQL、凭据或内部 URL。

## 公共阅读接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/` | 首页站点信息和文章列表 |
| GET | `/api/about` | 关于页 |
| GET | `/api/articles` | 首页文章列表 |
| GET | `/api/articles/archives` | 归档文章列表 |
| GET | `/api/articles/search` | 文章搜索 |
| GET | `/api/articles/{id}` | 文章元数据、导航和推荐 |
| GET | `/api/articles/{id}/content` | Markdown 正文流 |

首页、归档和搜索使用签名 cursor，`size` 范围为 1–50。关键词变化时客户端必须清空旧结果和 cursor；服务端验证 cursor 的查询指纹和过期时间。

## 管理接口

管理端只保留管理员登录、文章、关于、页面封面、网站基础配置、媒体存储、个人资料和菜单接口：

| 能力 | 接口 |
| --- | --- |
| 文章 | `/api/admin/articles*`、`/api/admin/articles/{id}/content`、`/versions`、`/images` |
| 页面/关于 | `/api/admin/pages*`、`/api/admin/about` |
| 网站 | `/api/admin/website/config`、`/api/admin/config/images` |
| 存储 | `/api/admin/storage/configs*` |
| 账户 | `/api/admin/users/password`、`/api/users/info`、`/api/users/avatar` |
| 菜单 | `/api/admin/user/menus` |

文章列表只返回元数据，不加载正文。管理端正文使用 `expectedVersion` 防止并发覆盖；冲突返回 409，编辑器必须保留本地草稿并让管理员决定覆盖或放弃。

## 文章内容与媒体

`tb_article` 只保存 `content_asset_id` 指针；正文是对象存储中的不可变 Markdown 版本，`tb_content_asset` 保存版本、大小、checksum、provider、`storage_config_id` 和状态。正文响应使用 `ETag`、`Last-Modified` 和合适的缓存策略；管理端正文使用 `no-store`。

文章封面、页面封面、管理员头像、关于内容和网站配置中的图片删除前必须经过媒体引用检查。存储配置响应只返回安全摘要，不返回明文凭据、密文、签名或任意 object key。

## Outbox

文章索引和媒体删除等可恢复副作用先写入 MySQL Outbox。Redis 关闭时由 DB worker 处理，Redis 开启时可通过 Streams 传输；handler 成功后才确认，失败事件按 eventId 幂等重试。Redis 不是事实源。

## 验收

接口变更至少验证 DTO/校验、认证/权限、空页、条件缓存、重复提交、409 冲突、相对代理路径，以及两个前端的 lint、test 和 build。
