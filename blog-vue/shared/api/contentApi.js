const DEFAULT_TTL_MS = 60 * 1000;
const DEFAULT_MAX_ENTRIES = 50;

function headerValue(headers, name) {
  if (!headers) return "";
  if (typeof headers.get === "function") return headers.get(name) || "";
  const key = Object.keys(headers).find(candidate => candidate.toLowerCase() === name.toLowerCase());
  return key ? headers[key] : "";
}

function responseBody(response) {
  if (typeof response?.data === "string") return response.data;
  if (typeof response?.data?.data === "string") return response.data.data;
  return "";
}

function createBoundedCache(maxEntries, now) {
  const entries = new Map();
  return {
    get(key) {
      const entry = entries.get(key);
      if (!entry || entry.expiresAt <= now()) {
        entries.delete(key);
        return null;
      }
      entries.delete(key);
      entries.set(key, entry);
      return entry;
    },
    set(key, value, ttlMs) {
      entries.delete(key);
      entries.set(key, { ...value, expiresAt: now() + ttlMs });
      while (entries.size > maxEntries) entries.delete(entries.keys().next().value);
    },
    clear() {
      entries.clear();
    },
    size() {
      return entries.size;
    }
  };
}

/**
 * Reads Markdown independently from article metadata and owns HTTP validators.
 * The cache is intentionally process-local and bounded: MySQL/object storage
 * remains authoritative, while a 304 can reuse a body already held by this tab.
 */
export function createContentApi(client, options = {}) {
  if (!client || typeof client.get !== "function") {
    throw new TypeError("createContentApi requires an HTTP client");
  }
  const now = options.now || (() => Date.now());
  const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
  const cache = createBoundedCache(options.maxEntries ?? DEFAULT_MAX_ENTRIES, now);
  const validators = new Map();

  async function read(articleId, { admin = false, config = {} } = {}) {
    const scope = admin ? "admin" : "public";
    const validatorKey = `${scope}:${articleId}`;
    const validator = validators.get(validatorKey);
    const headers = {
      ...(config.headers || {})
    };
    if (validator?.etag && !headers["If-None-Match"]) headers["If-None-Match"] = validator.etag;
    if (validator?.lastModified && !headers["If-Modified-Since"]) {
      headers["If-Modified-Since"] = validator.lastModified;
    }
    const url = admin
      ? `/api/admin/articles/${articleId}/content`
      : `/api/articles/${articleId}/content`;
    const response = await client.get(url, {
      ...config,
      responseType: "text",
      headers,
      validateStatus: status => (config.validateStatus
        ? config.validateStatus(status) || status === 304
        : (status >= 200 && status < 300) || status === 304)
    });
    const etag = headerValue(response.headers, "etag") || validator?.etag || null;
    const lastModified = headerValue(response.headers, "last-modified") || validator?.lastModified || null;
    if (response.status === 304) {
      const cached = validator?.cacheKey ? cache.get(validator.cacheKey) : null;
      if (cached) {
        return { data: cached.body, etag, lastModified, notModified: true };
      }
      // A validator without a body can happen after a tab restore. Retry once
      // without validators so a 304 can never become an empty successful page.
      validators.delete(validatorKey);
      return read(articleId, { admin, config: { ...config, headers: {} } });
    }
    const body = responseBody(response);
    if (body === "" && response.status !== 204) {
      throw new Error("文章内容为空");
    }
    const cacheKey = `${scope}:${articleId}:${etag || "current"}`;
    cache.set(cacheKey, { body }, ttlMs);
    validators.set(validatorKey, { etag, lastModified, cacheKey });
    return { data: body, etag, lastModified, notModified: false };
  }

  return {
    public: (articleId, config) => read(articleId, { config }),
    admin: (articleId, config) => read(articleId, { admin: true, config }),
    clear: () => {
      validators.clear();
      cache.clear();
    },
    cacheSize: () => cache.size()
  };
}
