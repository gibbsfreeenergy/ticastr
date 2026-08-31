# 前端工作区

`blog` 和 `admin` 是两个独立的 Vite 应用，各自维护 `package.json` 与
`package-lock.json`。`shared/` 只放不依赖 UI 框架的 HTTP、API、代理和运行时基础能力，应用通过相对路径导入。

```powershell
cd blog-vue/blog
npm ci
npm run dev

cd ../admin
npm ci
npm run dev -- --port 8081
```

提交前分别执行 `npm run lint`、`npm run test:run` 和 `npm run build`。业务请求使用 `/api`、`/uploads`、`/websocket` 相对路径，由 Vite/Nginx/Vercel 代理到 API。
