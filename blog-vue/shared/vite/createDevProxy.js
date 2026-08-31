export function createDevProxy(target = process.env.VITE_API_PROXY_TARGET || "http://localhost:8090") {
  return {
    "/api": {
      target,
      changeOrigin: true,
      rewrite: path => path.replace(/^\/api/, ""),
      ws: true
    },
    "/uploads": { target, changeOrigin: true },
    "/websocket": { target, changeOrigin: true, ws: true }
  };
}
