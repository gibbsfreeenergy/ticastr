const PAGES_ORIGIN = "https://ticastr-blog.pages.dev";
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

function createUpstreamRequest(request, targetUrl) {
  const headers = new Headers();
  for (const [name, value] of request.headers) {
    const lowerName = name.toLowerCase();
    if (REQUEST_HEADERS_TO_REPLACE.has(lowerName) || lowerName.startsWith("cf-")) {
      continue;
    }
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

function getApiOrigin(env) {
  const value = String(env?.API_ORIGIN || "").trim();
  if (!value) throw new Error("API_ORIGIN is not configured");

  const origin = new URL(value);
  if (
    !["http:", "https:"].includes(origin.protocol) ||
    origin.username ||
    origin.password ||
    origin.pathname !== "/" ||
    origin.search ||
    origin.hash
  ) {
    throw new Error("API_ORIGIN must be an HTTP(S) origin without credentials, path, query, or hash");
  }
  return origin;
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
  return { responseHeaders, setCookies };
}

function getProxyLabel(env) {
  return String(env?.PROXY_LABEL || "test").trim() || "test";
}

async function proxyApi(request, incomingUrl, env) {
  let backendOrigin;
  try {
    backendOrigin = getApiOrigin(env);
  } catch (error) {
    return new Response(JSON.stringify({ ok: false, message: error.message }), {
      status: 500,
      headers: { "Cache-Control": "no-store", "Content-Type": "application/json; charset=utf-8" }
    });
  }

  const targetUrl = new URL(backendOrigin);
  targetUrl.pathname = incomingUrl.pathname.slice("/api".length) || "/";
  targetUrl.search = incomingUrl.search;

  try {
    const upstreamResponse = await fetch(createUpstreamRequest(request, targetUrl), {
      cache: "no-store",
      redirect: "manual"
    });
    const { responseHeaders } = copyResponse(upstreamResponse);
    const location = responseHeaders.get("Location");
    if (location) responseHeaders.set("Location", rewriteLocation(location, backendOrigin, incomingUrl));
    responseHeaders.set("X-Ticastr-Pages-Proxy", `${getProxyLabel(env)}-api`);
    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders
    });
  } catch {
    return new Response(JSON.stringify({ ok: false, message: "代理暂时无法连接 API" }), {
      status: 502,
      headers: { "Cache-Control": "no-store", "Content-Type": "application/json; charset=utf-8" }
    });
  }
}

function healthResponse(request, env) {
  return new Response(
    JSON.stringify({
      ok: true,
      service: `ticastr-pages-proxy-${getProxyLabel(env)}`,
      target: PAGES_ORIGIN,
      apiConfigured: Boolean(env?.API_ORIGIN),
      host: new URL(request.url).host,
      time: new Date().toISOString()
    }),
    {
      headers: {
        "Cache-Control": "no-store",
        "Content-Type": "application/json; charset=utf-8",
        "X-Content-Type-Options": "nosniff"
      }
    }
  );
}

export default {
  async fetch(request, env) {
    const incomingUrl = new URL(request.url);
    if (incomingUrl.pathname === "/_proxy-health") {
      return healthResponse(request, env);
    }
    if (isApiPath(incomingUrl.pathname)) {
      return proxyApi(request, incomingUrl, env);
    }

    const targetUrl = new URL(PAGES_ORIGIN);
    targetUrl.pathname = incomingUrl.pathname;
    targetUrl.search = incomingUrl.search;

    try {
      const upstreamResponse = await fetch(createUpstreamRequest(request, targetUrl));
      const { responseHeaders } = copyResponse(upstreamResponse);

      const location = responseHeaders.get("Location");
      if (location) responseHeaders.set("Location", rewriteLocation(location, new URL(PAGES_ORIGIN), incomingUrl));
      responseHeaders.set("X-Ticastr-Pages-Proxy", getProxyLabel(env));

      return new Response(upstreamResponse.body, {
        status: upstreamResponse.status,
        statusText: upstreamResponse.statusText,
        headers: responseHeaders
      });
    } catch {
      return new Response(JSON.stringify({ ok: false, message: "代理暂时无法连接 Pages" }), {
        status: 502,
        headers: {
          "Cache-Control": "no-store",
          "Content-Type": "application/json; charset=utf-8"
        }
      });
    }
  }
};
