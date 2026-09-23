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

function jsonResponse(status, message) {
  return new Response(JSON.stringify({ code: 50000, message }), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

function getApiOrigin(env) {
  const value = String(env?.API_ORIGIN || "").trim();
  if (!value) throw new Error("API_ORIGIN is not configured");

  let origin;
  try {
    origin = new URL(value);
  } catch {
    throw new Error("API_ORIGIN must be a valid URL");
  }

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

function createUpstreamHeaders(request, incomingUrl) {
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
  headers.set("X-Forwarded-Host", incomingUrl.host);
  headers.set("X-Forwarded-Proto", incomingUrl.protocol.replace(":", ""));
  return headers;
}

function rewriteLocation(value, backendOrigin, incomingOrigin) {
  if (!value) return value;
  try {
    const location = new URL(value, backendOrigin);
    if (location.origin !== backendOrigin.origin) return value;
    return `${incomingOrigin}${location.pathname}${location.search}${location.hash}`;
  } catch {
    return value;
  }
}

export async function proxyToBackend(context, { prefix, stripPrefix }) {
  let backendOrigin;
  try {
    backendOrigin = getApiOrigin(context.env);
  } catch (error) {
    return jsonResponse(500, error.message);
  }

  const incomingRequest = context.request;
  const incomingUrl = new URL(incomingRequest.url);
  if (incomingUrl.origin === backendOrigin.origin) {
    return jsonResponse(500, "API_ORIGIN cannot point to the Pages site itself");
  }

  const targetUrl = new URL(backendOrigin);
  const targetPath = stripPrefix
    ? incomingUrl.pathname.slice(prefix.length) || "/"
    : incomingUrl.pathname;
  targetUrl.pathname = targetPath.startsWith("/") ? targetPath : `/${targetPath}`;
  targetUrl.search = incomingUrl.search;

  const method = incomingRequest.method.toUpperCase();
  const requestInit = {
    method,
    headers: createUpstreamHeaders(incomingRequest, incomingUrl),
    body: method === "GET" || method === "HEAD" ? undefined : incomingRequest.body,
    redirect: "manual"
  };
  if (requestInit.body) requestInit.duplex = "half";
  const upstreamRequest = new Request(targetUrl, requestInit);

  try {
    const upstreamResponse = await fetch(upstreamRequest, {
      cache: "no-store",
      redirect: "manual"
    });
    const responseHeaders = new Headers();
    const supportsSetCookieList = typeof upstreamResponse.headers.getSetCookie === "function";
    const setCookies = supportsSetCookieList
      ? upstreamResponse.headers.getSetCookie()
      : [];
    for (const [name, value] of upstreamResponse.headers) {
      const lowerName = name.toLowerCase();
      if (!HOP_BY_HOP_HEADERS.has(lowerName) && !(supportsSetCookieList && lowerName === "set-cookie")) {
        responseHeaders.set(name, value);
      }
    }
    for (const cookie of setCookies) {
      responseHeaders.append("Set-Cookie", cookie);
    }
    const location = responseHeaders.get("Location");
    if (location) {
      responseHeaders.set("Location", rewriteLocation(location, backendOrigin, incomingUrl.origin));
    }
    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders
    });
  } catch {
    return jsonResponse(502, "后端服务暂时不可用");
  }
}
