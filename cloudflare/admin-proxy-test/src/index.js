const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade"
]);

const REQUEST_HEADERS_TO_REPLACE = new Set([
  ...HOP_BY_HOP_HEADERS,
  "content-length",
  "host",
  "origin",
  "cf-connecting-ip",
  "cf-ray",
  "cf-visitor",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-proto"
]);

function configuredOrigin(env, name) {
  const value = String(env?.[name] || "").trim();
  if (!value) throw new Error(`${name} is not configured`);
  const origin = new URL(value);
  if (
    !["http:", "https:"].includes(origin.protocol) ||
    origin.username ||
    origin.password ||
    origin.pathname !== "/" ||
    origin.search ||
    origin.hash
  ) {
    throw new Error(`${name} must be an HTTP(S) origin without credentials, path, query, or hash`);
  }
  return origin;
}

function createUpstreamRequest(request, targetUrl) {
  const headers = new Headers();
  for (const [name, value] of request.headers) {
    const lowerName = name.toLowerCase();
    if (REQUEST_HEADERS_TO_REPLACE.has(lowerName) || lowerName.startsWith("cf-")) continue;
    headers.set(name, value);
  }

  const clientIp = request.headers.get("CF-Connecting-IP");
  if (clientIp) {
    headers.set("X-Real-IP", clientIp);
    headers.set("X-Forwarded-For", clientIp);
  }
  const incomingUrl = new URL(request.url);
  headers.set("X-Forwarded-Host", incomingUrl.host);
  headers.set("X-Forwarded-Proto", incomingUrl.protocol.replace(":", ""));

  const method = request.method.toUpperCase();
  const init = {
    method,
    headers,
    body: method === "GET" || method === "HEAD" ? undefined : request.body,
    redirect: "manual"
  };
  if (init.body) init.duplex = "half";
  return new Request(targetUrl, init);
}

function copyResponse(upstreamResponse) {
  const responseHeaders = new Headers();
  const supportsSetCookieList = typeof upstreamResponse.headers.getSetCookie === "function";
  const setCookies = supportsSetCookieList ? upstreamResponse.headers.getSetCookie() : [];
  for (const [name, value] of upstreamResponse.headers) {
    const lowerName = name.toLowerCase();
    if (!HOP_BY_HOP_HEADERS.has(lowerName) && !(supportsSetCookieList && lowerName === "set-cookie")) {
      responseHeaders.set(name, value);
    }
  }
  for (const cookie of setCookies) responseHeaders.append("Set-Cookie", cookie);
  return responseHeaders;
}

function rewriteLocation(value, backendOrigin, incomingUrl) {
  if (!value) return value;
  try {
    const location = new URL(value, backendOrigin);
    if (location.origin !== backendOrigin.origin) return value;
    return `${incomingUrl.origin}${location.pathname}${location.search}${location.hash}`;
  } catch {
    return value;
  }
}

function isApiPath(pathname) {
  return pathname === "/api" || pathname.startsWith("/api/");
}

function errorResponse(message) {
  return new Response(JSON.stringify({ ok: false, message }), {
    status: 502,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

async function proxy(request, incomingUrl, env, originName, stripPrefix = "") {
  let backendOrigin;
  try {
    backendOrigin = configuredOrigin(env, originName);
  } catch (error) {
    return new Response(JSON.stringify({ ok: false, message: error.message }), {
      status: 500,
      headers: { "Cache-Control": "no-store", "Content-Type": "application/json; charset=utf-8" }
    });
  }

  const targetUrl = new URL(backendOrigin);
  targetUrl.pathname = `${stripPrefix ? incomingUrl.pathname.slice(stripPrefix.length) : incomingUrl.pathname}` || "/";
  targetUrl.search = incomingUrl.search;
  try {
    const upstreamRequest = createUpstreamRequest(request, targetUrl);
    const upstream = originName === "API_ORIGIN" && env?.D1_API
      ? env.D1_API
      : globalThis;
    const upstreamResponse = await upstream.fetch(upstreamRequest, {
      cache: "no-store",
      redirect: "manual"
    });
    const responseHeaders = copyResponse(upstreamResponse);
    const location = responseHeaders.get("Location");
    if (location) responseHeaders.set("Location", rewriteLocation(location, backendOrigin, incomingUrl));
    responseHeaders.set("X-Ticastr-Admin-Proxy", String(env?.PROXY_LABEL || "admin-d1-test"));
    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders
    });
  } catch {
    return errorResponse(originName === "API_ORIGIN" ? "代理暂时无法连接 D1 API" : "代理暂时无法连接管理后台");
  }
}

function healthResponse(request, env) {
  return new Response(JSON.stringify({
    ok: true,
    service: String(env?.PROXY_LABEL || "admin-d1-test"),
    pagesOrigin: String(env?.PAGES_ORIGIN || ""),
    apiOrigin: String(env?.API_ORIGIN || ""),
    host: new URL(request.url).host,
    time: new Date().toISOString()
  }), {
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

export default {
  async fetch(request, env) {
    const incomingUrl = new URL(request.url);
    if (incomingUrl.pathname === "/_proxy-health") return healthResponse(request, env);
    if (isApiPath(incomingUrl.pathname)) return proxy(request, incomingUrl, env, "API_ORIGIN", "/api");
    return proxy(request, incomingUrl, env, "PAGES_ORIGIN");
  }
};
