import { proxyToBackend } from "../../cloudflare-proxy.js";

export function onRequest(context) {
  return proxyToBackend(context, { prefix: "/api", stripPrefix: true });
}
