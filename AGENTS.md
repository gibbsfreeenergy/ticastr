# AGENTS.md

## 项目概览

`ticastr` 是一个个人博客系统，由一个 Spring Boot API 和两个 Vue 2 单页应用组成：

- `blog-springboot/`：Java 8 / Spring Boot 2.4 后端，默认监听 `8090`。
- `blog-vue/blog/`：面向访客的博客前台。
- `blog-vue/admin/`：面向管理员的后台。
- `blog-mysql8.sql`：MySQL 8 初始化结构与示例数据。

两个前端开发服务器都会将 `/api` 转发至 `http://localhost:8090`，并去掉 `/api` 前缀。

## 开发与验证

在修改前，先确认所改目录的 `package.json` 或 `pom.xml` 中实际定义的命令。常用命令如下：

| 目标 | 目录 | 命令 |
| --- | --- | --- |
| 启动后端 | `blog-springboot` | `mvn spring-boot:run` |
| 构建并执行后端测试 | `blog-springboot` | `mvn test` 或 `mvn package` |
| 启动博客前台 | `blog-vue/blog` | `npm ci`，然后 `npm run serve` |
| 检查/构建博客前台 | `blog-vue/blog` | `npm run lint` / `npm run build` |
| 启动管理后台 | `blog-vue/admin` | `npm ci`，然后 `npm run serve -- --port 8081` |
| 检查/构建管理后台 | `blog-vue/admin` | `npm run lint` / `npm run build` |

两个 Vue 开发服务器不能占用同一个端口；若同时启动，显式为其中一个指定端口。仓库当前未包含自动化测试源码，因此至少对改动的模块运行对应的 lint 或 build，并在提交说明中记录未运行的检查及原因。

## 代码边界与约定

- 后端按 `controller -> service -> dao` 分层；控制器位于 `blog-springboot/src/main/java/com/wzh/blog/controller`，MyBatis mapper 位于 `src/main/resources/mapper`。新增持久化查询时，同步维护 DAO 与 mapper XML。
- 后端实体、DTO、VO 各自位于同名包中；不要把 API 输入输出对象直接替换为数据库实体，除非现有相邻代码已采用该模式。
- 访客端路由在 `blog-vue/blog/src/router/index.js`，后台的静态登录路由在 `blog-vue/admin/src/router/index.js`；后台其余菜单由接口数据驱动。
- 前端请求统一使用相对的 `/api/...` 路径，以保持本地代理与部署反向代理兼容。不要把 `localhost:8090` 写入业务组件。
- Vue 组件沿用现有 Vue 2 Options API、双引号与项目 ESLint/Prettier 配置。改动组件时，保持 `views`、`components`、`store`、`assets` 的既有职责划分。
- 不要提交构建产物、`node_modules`、IDE 设置或本地环境文件。扩展 `.gitignore` 时优先使用项目级通用规则。

## 配置与数据安全

- `blog-springboot/src/main/resources/application.yml` 包含数据库、Redis、RabbitMQ、邮件、搜索、对象存储和 OAuth 等运行配置。提交前不得把真实密码、令牌、私钥或新的凭据写入代码、文档或 SQL 文件。
- 若需要调整配置，优先引入环境变量或本地覆盖方案，并提供不含秘密的示例值；不要在 README 中复制现有的敏感字段。
- `blog-mysql8.sql` 会删除并重建表，且带有示例记录。仅在专用本地数据库中导入；修改它前先确认数据迁移影响。

## 提交前检查

1. 审阅 `git diff --check` 与 `git diff`，确保没有无关改动或敏感信息。
2. 对受影响模块运行最贴近的 lint、测试或构建命令。
3. 更新 README 或相关说明，若启动方式、配置项、路由或数据库初始化方式发生变化。
4. 只暂存本次任务涉及的文件，并用简洁、描述性的提交信息提交。
