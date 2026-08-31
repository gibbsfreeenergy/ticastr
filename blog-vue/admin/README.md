# 管理控制台

本应用使用 Node 24、Vite 8、Vue 3 和 Element Plus。

```powershell
npm ci
npm run dev -- --port 8081
```

```powershell
npm run lint
npm run test:run
npm run build
npm run verify:budget
```

管理端菜单由 API 返回的稳定 `routeKey` 驱动，前端显式注册组件，不依赖后端源文件路径。开发服务器将 `/api`、`/uploads` 和 `/websocket` 代理到 API。
