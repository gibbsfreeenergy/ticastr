# Cloudflare Pages 前端部署

本阶段只迁移两个 Vue 前端，Spring Boot、MySQL、Redis 和上传文件继续运行在现有服务器上。浏览器仍然只访问相对的 `/api` 和 `/uploads` 路径：

```text
浏览器
  ├─ 静态页面、JS、CSS、图片      -> Cloudflare Pages
  └─ /api/*、/uploads/*            -> Pages Functions
                                      -> API_ORIGIN
                                      -> 现有 Spring Boot API
```

Pages Functions 会去掉 `/api` 前缀后访问后端，例如 `/api/login` 会转发到后端的 `/login`；`/uploads` 会保持原路径。代理会转发 Cookie、CSRF 请求头和请求体，并移除浏览器的 `Origin`，这样登录 Session 仍然以 Pages 域名为同源 Cookie，不需要把 API 暴露给前端代码。

## 两个 Pages 项目

默认项目名已经写入 `.github/workflows/deploy-pages.yml`：

| 应用 | Pages 项目 | 源码目录 | 构建命令 | 输出目录 |
| --- | --- | --- | --- | --- |
| 公共博客 | `ticastr-blog` | `blog-vue/blog` | `npm run build` | `dist` |
| 管理后台 | `ticastr-admin` | `blog-vue/admin` | `npm run build` | `dist` |

如果 Cloudflare 账户中已有其他项目名，只需同步修改 workflow 矩阵里的 `project`，不需要改前端业务代码。

## 首次 Cloudflare 配置

需要在本机登录 Wrangler，或在已经登录 Cloudflare 的环境中执行。下面命令只创建 Pages 项目，不会修改服务器：

```powershell
npx wrangler@latest login
npx wrangler@latest pages project create ticastr-blog --production-branch master
npx wrangler@latest pages project create ticastr-admin --production-branch master
```

为两个项目配置同一个后端上游。`API_ORIGIN` 必须是后端根地址，能直接访问 `/login`、`/articles` 和 `/uploads`，不能填前端地址，也不能带 `/api` 路径：

```powershell
npx wrangler@latest pages secret put API_ORIGIN --project-name ticastr-blog
npx wrangler@latest pages secret put API_ORIGIN --project-name ticastr-admin
```

命令会交互式读取值。生产环境建议使用服务器的 HTTPS 入口；如果暂时使用服务器 IP 和 `8090` 端口，需要先确认 Cloudflare 边缘能够访问该端口，并在服务器防火墙中只允许必要流量。更稳妥的做法是给 API 配置 HTTPS 域名，或通过 Cloudflare Tunnel 暴露后端。

本地验证 Pages Functions：

```powershell
cd blog-vue/blog
Copy-Item .dev.vars.example .dev.vars
npm ci
npm run build
npx wrangler@latest pages dev
```

管理后台同理，在 `blog-vue/admin` 目录执行。`.dev.vars` 已被 Git 忽略，不能提交。

## GitHub Actions

`.github/workflows/deploy-pages.yml` 会在 `master` 的 `blog-vue/**` 发生变化时分别构建并部署两个 Pages 项目。仓库需要配置：

- `CLOUDFLARE_API_TOKEN`：只授予目标账户 Pages 编辑权限的 API Token。
- `CLOUDFLARE_ACCOUNT_ID`：Cloudflare 账户 ID。
- 可选 `TICASTR_PAGES_API_ORIGIN`：设置后，Actions 会在每次部署前同步更新两个项目的 `API_ORIGIN` Secret；未设置时会临时复用已有的 `TICASTR_DEPLOY_HOST` 并指向 `http://<服务器>:8090`。后续建议改成 HTTPS API 域名。

首次部署时 workflow 会在目标 Cloudflare 账户中幂等创建两个 Pages 项目，并同步写入 `API_ORIGIN` Secret。项目名来自 workflow 矩阵；如果账户中已有同名项目，会直接复用。

前端迁移完成后，再把 Cloudflare Pages 的生产域名加入服务器 `.env` 的 `CORS_ALLOWED_ORIGINS` 和 `WEBSITE_URL`，然后重启 API。虽然同源代理不依赖 CORS，但保留旧入口或进行直接 API 调试时仍需要正确的允许来源。

## 回滚

现有服务器上的 `8080` 公共站点和 `8081` 管理后台保持不变，Cloudflare Pages 只是新增入口。若 Pages 配置或上游不可用，可以继续使用原服务器入口；修复后再在 Pages 项目中重新部署。
