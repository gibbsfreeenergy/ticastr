# Ticastr 核心架构

## 产品边界

公共博客只保留四条阅读路径：

```text
首页 -> 搜索/归档 -> 文章详情 -> 关于
```

首页展示文章列表和站点信息；搜索、归档和文章详情提供只读阅读能力；关于页展示 Markdown 内容。管理端只保留管理员登录、文章编辑、页面封面、网站基础配置、媒体存储和个人设置。

## 当前架构

```text
Vue public/admin
        |
   relative HTTP
        |
Spring controller -> application service -> DAO/port
        |                    |
      MySQL              object storage
        |
  transactional outbox -> DB worker
                         \\-> optional Redis Streams
```

- MySQL 是唯一持久事实源。
- 文章元数据和 Markdown 正文分离，文章只保存当前内容资产指针；内容资产保存版本、checksum、ETag 和存储配置 ID。
- 首页、归档和搜索使用签名 cursor；搜索使用可重建的本地 Lucene 索引。
- Redis 只提供可选缓存、限流、锁、Session、去重和 Outbox 传输；关闭或故障时使用各自的本地 fallback。
- 图片和 Markdown 通过 provider-neutral storage port 访问，管理端只能操作安全配置摘要。
- Flyway `V26__trim_to_core_blog.sql` 删除已退役的表、字段、权限和数据，并重建核心菜单/资源。

## 设计约束

- 不在列表接口加载正文，不把正文重新写回文章主表。
- 不在前端拼接 provider URL，不把数据库字段当作远程组件路径执行。
- 删除媒体前必须通过核心文章、页面、头像、关于内容和网站配置引用检查。
- Redis、Lucene、浏览器缓存都可以清空重建，不能承载事实数据。
- 后端保持 `controller -> service -> dao`，实体、DTO、VO 分离。

## 验证标准

改动完成后必须验证：V26 可从 legacy baseline 升级；管理员菜单只包含保留能力；后端 `mvn clean test` 通过；两个前端的 lint、test、build 和 bundle budget 通过；代理只保留 `/api`、`/uploads`；没有旧页面、接口、表或菜单的运行时引用。
