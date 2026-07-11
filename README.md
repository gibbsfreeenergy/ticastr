# ticastr

一个基于 Spring Boot 与 Vue 2 的个人博客系统，包含访客端博客、管理后台和 REST API。线上站点：[ticastr.com](https://ticastr.com)。

## 技术栈

- 后端：Java 8、Spring Boot 2.4、Spring Security、MyBatis-Plus、WebSocket。
- 数据与基础设施：MySQL 8、Redis、RabbitMQ、Elasticsearch、邮件服务和对象存储（本地、OSS、COS、OBS）。
- 前端：Vue 2、Vue Router、Vuex；访客端使用 Vuetify，管理后台使用 Element UI。

## 仓库结构

```text
.
├── blog-springboot/       # Spring Boot 后端 API
├── blog-vue/
│   ├── blog/              # 访客端博客
│   └── admin/             # 管理后台
├── blog-mysql8.sql        # MySQL 8 初始化脚本与示例数据
├── AGENTS.md              # 贡献与协作说明
└── README.md
```

## 功能概览

- 文章、分类、标签、归档、搜索与点赞。
- 说说、留言、评论与回复、相册和友情链接。
- 用户注册、登录、QQ/微博 OAuth、个人资料与聊天室。
- 后台内容管理、用户与角色权限、菜单资源、站点配置及操作日志。

## 本地启动

### 1. 准备依赖

需要 Java 8、Maven、Node.js（建议使用与 `package-lock.json` 兼容的 npm）以及 MySQL 8。完整运行还依赖 Redis、RabbitMQ、Elasticsearch、邮件服务和对象存储；可按本地开发目标配置相应服务。

创建一个专用的本地 MySQL 数据库，并导入 `blog-mysql8.sql`：

```bash
mysql -u <user> -p <database> < blog-mysql8.sql
```

> 注意：该脚本会删除并重建其中定义的表，且带有示例数据。请勿导入到生产库。

### 2. 配置后端

根据本地环境检查并更新 `blog-springboot/src/main/resources/application.yml` 中的连接信息和第三方服务配置。不要提交密码、令牌、私钥或其他真实凭据。

启动 API：

```bash
cd blog-springboot
mvn spring-boot:run
```

默认地址为 `http://localhost:8090`。

### 3. 启动前端

访客端：

```bash
cd blog-vue/blog
npm ci
npm run serve
```

管理后台（与访客端同时运行时指定不同端口）：

```bash
cd blog-vue/admin
npm ci
npm run serve -- --port 8081
```

两个前端应用都将 `/api` 请求代理到 `http://localhost:8090`，并自动移除 `/api` 前缀。

## 构建与质量检查

```bash
# 后端：在 blog-springboot/ 中执行
mvn test
mvn package

# 访客端：在 blog-vue/blog/ 中执行
npm run lint
npm run build

# 管理后台：在 blog-vue/admin/ 中执行
npm run lint
npm run build
```

仓库目前没有测试源码。提交前请至少运行受影响模块的 lint 或 build，并检查变更中没有配置凭据或构建产物。

## 开发说明

- 后端控制器、业务服务、DAO 和 mapper 分别位于 `controller`、`service`、`dao` 与 `resources/mapper`；新增查询时应同步维护 DAO 与 mapper XML。
- 访客端路由在 `blog-vue/blog/src/router/index.js`；后台登录路由在 `blog-vue/admin/src/router/index.js`，其余菜单由接口返回的数据驱动。
- 前端接口请保持 `/api/...` 相对路径，避免将本地后端地址硬编码到组件中。
- 详细协作规范请阅读 [AGENTS.md](AGENTS.md)。

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 发布。
