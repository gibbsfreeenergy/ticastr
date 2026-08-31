# 公共博客站点

本应用使用 Node 24、Vite 8、Vue 3 和 Vue Router 5。

```powershell
npm ci
npm run dev
```

```powershell
npm run lint
npm run test:run
npm run build
npm run verify:budget
```

开发服务器默认使用 8080，并将 `/api`、`/uploads` 和 `/websocket` 代理到 API。生产构建可使用 `PUBLIC_SITE_ORIGIN`、`PRERENDER_API_URL` 执行 `npm run build:seo`，生成文章静态页、sitemap、robots、RSS/Atom feed；生产环境通过部署平台注入的相对路径代理访问 API。
