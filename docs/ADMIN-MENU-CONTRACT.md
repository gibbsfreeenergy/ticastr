# 管理端菜单与路由契约

管理端菜单由后端授权元数据和前端显式路由注册共同组成。菜单只负责展示和导航，接口权限仍由后端资源授权控制。

## 保留的 route key

当前只保留以下菜单：

| route key | 用途 |
| --- | --- |
| `home` | 管理端首页 |
| `articleGroup` | 文章管理分组 |
| `articleList` | 文章列表 |
| `article` | 文章编辑器（隐藏菜单项） |
| `about` | 关于我内容 |
| `page` | 首页、归档、关于页面封面 |
| `website` | 网站基础配置 |
| `setting` | 管理员资料和密码 |
| `storage` | 图片和 Markdown 的对象存储配置 |

分组只有 `workspace`、`content`、`settings` 三类。`routeKey` 只允许从 `blog-vue/admin/src/assets/js/routeRegistry.js` 显式注册的页面中选择，未知 key 必须在菜单加载时失败，不能把数据库字段当作远程组件路径执行。

## 返回字段

`GET /api/admin/user/menus` 返回树形菜单。`code`、`routeKey`、`section`、`iconKey` 是稳定协议字段；`name` 和 `orderNum` 只影响展示；`children` 表示子菜单。旧的 `component`、`icon` 字段仅用于历史迁移数据，不再驱动动态组件发现。

## 数据库与权限

Flyway `V26__trim_to_core_blog.sql` 会清空旧菜单、资源和角色关联，并重建上述菜单与公开/管理员 API 资源。迁移后只保留管理员角色，隐藏的 `article` 路由仍保留用于编辑器导航，但不会作为独立侧栏入口显示。

修改菜单时必须同步后端迁移/授权资源、`menuMetadata.js`、`routeRegistry.js` 和相应测试；隐藏菜单不能绕过后端鉴权。
