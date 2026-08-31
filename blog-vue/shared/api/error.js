const STATUS_MESSAGES = Object.freeze({
  400: "请求参数有误，请检查后重试",
  401: "登录已过期，请重新登录",
  403: "没有执行此操作的权限",
  404: "请求的内容不存在",
  409: "数据已发生变化，请刷新后重试",
  429: "操作过于频繁，请稍后重试",
  500: "服务暂时不可用，请稍后重试",
  502: "服务暂时不可用，请稍后重试",
  503: "服务暂时不可用，请稍后重试",
  504: "服务响应超时，请稍后重试"
});

function headerValue(headers, name) {
  if (!headers) return "";
  if (typeof headers.get === "function") return headers.get(name) || "";
  const key = Object.keys(headers).find(candidate => candidate.toLowerCase() === name.toLowerCase());
  return key ? headers[key] : "";
}

function retryAfterSeconds(headers) {
  const value = Number.parseInt(headerValue(headers, "retry-after"), 10);
  return Number.isFinite(value) && value >= 0 ? value : null;
}

export function normalizeHttpError(error) {
  if (error?.kind && typeof error.message === "string") return error;
  const status = error?.response?.status ?? null;
  const timedOut = error?.code === "ECONNABORTED" || error?.code === "ETIMEDOUT";
  const networkFailure = !error?.response;
  const retryable = timedOut || networkFailure || status === 429 || status >= 500;
  let kind = "unknown";
  if (status === 401) kind = "auth-expired";
  else if (status === 403) kind = "forbidden";
  else if (status === 404) kind = "not-found";
  else if (status === 409) kind = "conflict";
  else if (status === 429) kind = "rate-limited";
  else if (status >= 500) kind = "server";
  else if (timedOut) kind = "timeout";
  else if (networkFailure) kind = "network";
  else if (status >= 400) kind = "request";

  return {
    kind,
    status,
    message: timedOut
      ? "请求超时，请检查网络后重试"
      : status && STATUS_MESSAGES[status]
        ? STATUS_MESSAGES[status]
        : networkFailure
          ? "网络请求失败，请稍后重试"
          : "请求失败，请稍后重试",
    retryable,
    retryAfterSeconds: retryAfterSeconds(error?.response?.headers),
    traceId: headerValue(error?.response?.headers, "x-request-id")
      || headerValue(error?.response?.headers, "trace-id")
      || null,
    cause: error
  };
}

export function getSafeErrorMessage(error) {
  return normalizeHttpError(error).message;
}
