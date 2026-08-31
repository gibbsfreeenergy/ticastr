# 管理端菜单与路由契约

管理端菜单是“后端授权元数据 + 前端路由注册”的边界，不是前端源文件路径的远程执行机制。契约的目标是允许修改展示文案、图标和排序，而不改变权限 key 或前端构建目录。

## 返回字段

`GET /api/admin/user/menus` 返回统一 `Result`，`data` 为树形菜单。每个节点使用以下字段：

| 字段 | 责任 | 是否稳定 |
| --- | --- | --- |
| `code` | 菜单/能力的稳定标识；兼容期间默认与 `routeKey` 相同 | 是 |
| `routeKey` | 前端 route registry 的查找 key | 是 |
| `section` | 侧栏分组，例如 `workspace`、`content`、`community`、`settings` | 是 |
| `iconKey` | 前端图标映射 key | 是 |
| `name` | 展示名称 | 否，可改名/国际化 |
| `path` | 管理端 URL path | 路由节点稳定；不要用来推导组件文件 |
| `orderNum` | 同级排序 | 否 |
| `children` | 子菜单 | 结构字段 |

`component` 和旧 `icon` 字段仅为 V7 滚动迁移兼容保留。新数据不得把 `/src/views/...` 当成业务协议；前端只接受显式 `routeKey`，未知 key 会在菜单加载/测试阶段失败。

## 已知 route key

当前 registry 覆盖 `home`、`article`、`articleList`、`category`、`tag`、`album`、`photo`、`albumDelete`、`comment`、`message`、`user`、`online`、`role`、`resource`、`menu`、`friendLink`、`about`、`operation`、`page`、`website`、`setting`、`talk`、`talkList`，以及对应的 `articleGroup`、`messageGroup`、`systemGroup`、`userGroup`、`permissionGroup`、`albumGroup`、`talkGroup`、`logGroup` 分组节点。

后端的 `MenuRouteContract` 负责把旧 `path/component` 映射为稳定 key；Flyway `V7__menu_route_contract.sql` 为已有数据补齐字段。迁移期间保留旧字段是为了支持滚动发布，不代表恢复三方耦合。

## 权限与显示边界

- 后端 URL 资源权限独立于前端是否显示菜单；隐藏菜单不能绕过后端鉴权。
- `routeKey` 只决定可加载的前端页面，不能作为安全授权凭据。
- `name`、`iconKey`、`section` 只影响展示，不参与权限判断。
- 新增菜单必须同时更新 `MenuRouteContract`、前端 `routeRegistry.js`、本文件和相应测试；未知 key 不允许静默降级到任意组件。

## 变更验收

1. 修改名称后，route key、路径、分组和后端授权不变。
2. 管理端菜单单测能覆盖成功加载和未知 route key 失败。
3. Flyway 在 legacy baseline 和 fresh schema 上都能完成字段补齐。
4. 数据库中不再新增前端源文件路径依赖。
