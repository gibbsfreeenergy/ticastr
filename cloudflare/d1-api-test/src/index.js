import bcrypt from "bcryptjs";

const MAX_PAGE_SIZE = 50;
const CURSOR_LIFETIME_SECONDS = 900;
const MAX_MARKDOWN_BYTES = 1_048_576;
const MAX_JSON_BODY_BYTES = 2 * 1024 * 1024;
const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const SUCCESS_CODE = 20000;
const BAD_REQUEST_CODE = 40000;
const UNAUTHORIZED_CODE = 40100;
const FORBIDDEN_CODE = 40300;
const NOT_FOUND_CODE = 40400;
const CONFLICT_CODE = 40900;
const NOT_IMPLEMENTED_CODE = 50100;
const SESSION_COOKIE = "TICASTR_SESSION";
const CSRF_COOKIE = "XSRF-TOKEN";
const SESSION_PREFIX = "d1api:session:";
const UNSUPPORTED_MENU_CODES = new Set(["storage"]);
const WEBSITE_CONFIG_KEYS = [
  "websiteAvatar", "websiteName", "websiteAuthor", "websiteIntro", "websiteNotice",
  "websiteCreateTime", "websiteRecordNo", "socialUrlList", "qq", "github", "gitee"
];

const encoder = new TextEncoder();
const decoder = new TextDecoder();

class HttpError extends Error {
  constructor(status, code, message, data = null) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.code = code;
    this.data = data;
  }
}

function jsonResponse(payload, status = 200, extraHeaders = {}) {
  const headers = new Headers({
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
    "X-Content-Type-Options": "nosniff",
    "X-Ticastr-Data-Plane": "d1",
    "Access-Control-Allow-Origin": "*",
    ...extraHeaders
  });
  return new Response(JSON.stringify(payload), { status, headers });
}

function result(data, message = "操作成功") {
  return { flag: true, code: SUCCESS_CODE, message, data };
}

function failure(code, message, status = 400, data = null) {
  return jsonResponse({ flag: false, code, message, data }, status);
}

function copyResponse(response, headers) {
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

function isAllowedOrigin(origin) {
  if (!origin) return false;
  try {
    const url = new URL(origin);
    if (url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname)) return true;
    return url.protocol === "https:" && (url.hostname === "ticastr.cn" || url.hostname.endsWith(".ticastr.cn"));
  } catch {
    return false;
  }
}

function withCors(response, request) {
  const headers = new Headers(response.headers);
  const origin = request.headers.get("Origin");
  if (origin && isAllowedOrigin(origin)) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Credentials", "true");
    headers.append("Vary", "Origin");
  } else if (!origin) {
    headers.set("Access-Control-Allow-Origin", "*");
  }
  headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, If-None-Match, If-Modified-Since, X-XSRF-TOKEN");
  return copyResponse(response, headers);
}

function appendCookies(response, cookies) {
  const headers = new Headers(response.headers);
  for (const cookie of cookies) headers.append("Set-Cookie", cookie);
  return copyResponse(response, headers);
}

function serializeCookie(name, value, options = {}) {
  const parts = [`${name}=${encodeURIComponent(value)}`, "Path=/", "SameSite=Lax", "Secure"];
  if (options.httpOnly) parts.push("HttpOnly");
  if (options.maxAge != null) parts.push(`Max-Age=${Math.max(0, Math.floor(options.maxAge))}`);
  return parts.join("; ");
}

function parseCookies(request) {
  const header = request.headers.get("Cookie") || "";
  const cookies = {};
  for (const item of header.split(";")) {
    const separator = item.indexOf("=");
    if (separator < 1) continue;
    const name = item.slice(0, separator).trim();
    const value = item.slice(separator + 1).trim();
    try {
      cookies[name] = decodeURIComponent(value);
    } catch {
      cookies[name] = value;
    }
  }
  return cookies;
}

function cookieValue(request, name) {
  return parseCookies(request)[name] || null;
}

function constantTimeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string") return false;
  const leftBytes = encoder.encode(left);
  const rightBytes = encoder.encode(right);
  let difference = leftBytes.length ^ rightBytes.length;
  const length = Math.max(leftBytes.length, rightBytes.length);
  for (let index = 0; index < length; index++) {
    difference |= (leftBytes[index] || 0) ^ (rightBytes[index] || 0);
  }
  return difference === 0;
}

function randomToken() {
  return `${crypto.randomUUID()}${crypto.randomUUID()}`.replaceAll("-", "");
}

function sessionTtl(env) {
  const configured = Number(env.SESSION_TTL_SECONDS);
  if (!Number.isFinite(configured)) return 21_600;
  return Math.min(86_400, Math.max(300, Math.floor(configured)));
}

function sessionKey(token) {
  return `${SESSION_PREFIX}${token}`;
}

async function readSession(request, env, refresh = true) {
  const token = cookieValue(request, SESSION_COOKIE);
  if (!token || !env.KV) return null;
  const raw = await env.KV.get(sessionKey(token));
  if (!raw) return null;
  let session;
  try {
    session = JSON.parse(raw);
  } catch {
    await env.KV.delete(sessionKey(token));
    return null;
  }
  if (!session?.userInfoId || !session?.csrf || !session?.authId) return null;
  const user = await env.DB.prepare("SELECT is_disable FROM tb_user_info WHERE id = ?").bind(session.userInfoId).first();
  if (!user || Number(user.is_disable) === 1) {
    await env.KV.delete(sessionKey(token));
    return null;
  }
  if (refresh) await env.KV.put(sessionKey(token), JSON.stringify(session), { expirationTtl: sessionTtl(env) });
  return { ...session, token };
}

async function storeSession(env, session) {
  if (!env.KV) throw new Error("KV binding is not configured");
  await env.KV.put(sessionKey(session.token), JSON.stringify(session), { expirationTtl: sessionTtl(env) });
}

async function loadLoginUser(env, username) {
  const user = await env.DB.prepare(`
    SELECT ua.id AS auth_id, ua.user_info_id, ua.username, ua.password, ua.login_type,
           ui.email, ui.nickname, ui.avatar, ui.intro, ui.web_site, ui.is_disable
    FROM tb_user_auth ua
    JOIN tb_user_info ui ON ui.id = ua.user_info_id
    WHERE ua.username = ?
    LIMIT 1`).bind(username).first();
  if (!user) return null;
  const roles = await env.DB.prepare(`
    SELECT r.role_name
    FROM tb_user_role ur
    JOIN tb_role r ON r.id = ur.role_id
    WHERE ur.user_id = ? AND r.is_disable = 0
    ORDER BY r.id`).bind(user.user_info_id).all();
  return {
    ...user,
    roles: (roles.results || []).map(row => row.role_name)
  };
}

function userResponse(user, env) {
  return {
    id: Number(user.auth_id),
    userInfoId: Number(user.user_info_id),
    email: user.email,
    loginType: Number(user.login_type),
    username: user.username,
    roleList: user.roles || [],
    nickname: user.nickname,
    avatar: rewriteAssetUrl(user.avatar, env),
    intro: user.intro,
    webSite: user.web_site
  };
}

async function login(request, env) {
  const form = await request.formData();
  const username = String(form.get("username") || "").trim();
  const password = String(form.get("password") || "");
  if (!username || !password) return failure(BAD_REQUEST_CODE, "用户名和密码不能为空", 400);
  const user = await loadLoginUser(env, username);
  let matches = false;
  if (user && Number(user.is_disable) !== 1) {
    try {
      matches = await bcrypt.compare(password, user.password);
    } catch {
      matches = false;
    }
  }
  if (!user || !matches) return failure(BAD_REQUEST_CODE, "用户名或密码错误", 200);
  const token = randomToken();
  const csrf = randomToken();
  await storeSession(env, {
    token,
    authId: Number(user.auth_id),
    userInfoId: Number(user.user_info_id),
    username: user.username,
    roles: user.roles,
    csrf,
    createdAt: new Date().toISOString()
  });
  const response = jsonResponse(result(userResponse(user, env)));
  return appendCookies(response, [
    serializeCookie(SESSION_COOKIE, token, { httpOnly: true, maxAge: sessionTtl(env) }),
    serializeCookie(CSRF_COOKIE, csrf, { maxAge: sessionTtl(env) })
  ]);
}

async function logout(request, env) {
  const token = cookieValue(request, SESSION_COOKIE);
  if (token && env.KV) await env.KV.delete(sessionKey(token));
  const response = jsonResponse(result(null));
  return appendCookies(response, [
    serializeCookie(SESSION_COOKIE, "", { httpOnly: true, maxAge: 0 }),
    serializeCookie(CSRF_COOKIE, "", { maxAge: 0 })
  ]);
}

function isMutatingMethod(method) {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(method);
}

function hasPathPermission(pattern, path) {
  const escaped = String(pattern).split("*").map(part => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join(".*");
  return new RegExp(`^${escaped}$`).test(path);
}

async function hasPermission(env, session, path, method) {
  const rows = await env.DB.prepare(`
    SELECT r.url, r.request_method
    FROM tb_user_role ur
    JOIN tb_role_resource rr ON rr.role_id = ur.role_id
    JOIN tb_resource r ON r.id = rr.resource_id
    WHERE ur.user_id = ? AND (r.request_method IS NULL OR r.request_method = ?)`)
    .bind(session.userInfoId, method).all();
  return (rows.results || []).some(row => row.url && hasPathPermission(row.url, path));
}

function readPositiveInteger(value, fallback, label, maximum = Number.MAX_SAFE_INTEGER) {
  if (value == null || value === "") return fallback;
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1 || number > maximum) {
    throw new HttpError(400, BAD_REQUEST_CODE, `${label} 参数无效`);
  }
  return number;
}

async function readBodyText(request, maxBytes = MAX_JSON_BODY_BYTES) {
  const contentLength = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(contentLength) && contentLength > maxBytes) {
    throw new HttpError(413, BAD_REQUEST_CODE, "请求体过大");
  }
  const text = await request.text();
  if (encoder.encode(text).byteLength > maxBytes) throw new HttpError(413, BAD_REQUEST_CODE, "请求体过大");
  return text;
}

async function readJson(request, maxBytes = MAX_JSON_BODY_BYTES) {
  const text = await readBodyText(request, maxBytes);
  if (!text.trim()) return {};
  try {
    return JSON.parse(text);
  } catch {
    throw new HttpError(400, BAD_REQUEST_CODE, "请求体不是有效 JSON");
  }
}

function nowSql() {
  return new Date().toISOString().slice(0, 19).replace("T", " ");
}

function base64UrlEncode(bytes) {
  let value = "";
  for (const byte of bytes) value += String.fromCharCode(byte);
  return btoa(value).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function base64UrlDecode(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid base64url");
  const padded = value.replaceAll("-", "+").replaceAll("_", "/")
    + "=".repeat((4 - (value.length % 4)) % 4);
  const decoded = atob(padded);
  return Uint8Array.from(decoded, character => character.charCodeAt(0));
}

async function signingKey(env) {
  const secret = String(env.CURSOR_SECRET || "").trim();
  if (!secret) throw new Error("CURSOR_SECRET is not configured");
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

async function signCursor(env, payload) {
  const expiresAt = Math.floor(Date.now() / 1000) + CURSOR_LIFETIME_SECONDS;
  const body = JSON.stringify({ ...payload, expiresAt });
  const key = await signingKey(env);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(body));
  return `${base64UrlEncode(encoder.encode(body))}.${base64UrlEncode(new Uint8Array(signature))}`;
}

async function readCursor(env, encoded, expectedFeed) {
  if (!encoded) return null;
  try {
    const separator = encoded.lastIndexOf(".");
    if (separator < 1) throw new Error("invalid cursor");
    const body = decoder.decode(base64UrlDecode(encoded.slice(0, separator)));
    const signature = base64UrlDecode(encoded.slice(separator + 1));
    const key = await signingKey(env);
    const expected = await crypto.subtle.sign("HMAC", key, encoder.encode(body));
    if (!constantTimeEqual(base64UrlEncode(signature), base64UrlEncode(new Uint8Array(expected)))) {
      throw new Error("invalid cursor signature");
    }
    const payload = JSON.parse(body);
    if (payload.feed !== expectedFeed || payload.expiresAt <= Math.floor(Date.now() / 1000)) {
      throw new Error("expired or mismatched cursor");
    }
    return payload;
  } catch {
    throw new HttpError(400, BAD_REQUEST_CODE, "游标无效或已过期");
  }
}

function parsePageSize(url, fallback = 10) {
  const raw = url.searchParams.get("size");
  if (raw === null || raw === "") return fallback;
  return readPositiveInteger(raw, fallback, "size", MAX_PAGE_SIZE);
}

function isoDate(value) {
  if (!value) return value;
  return String(value).replace(" ", "T");
}

function pageCursor(row) {
  return { time: row.create_time, id: Number(row.id) };
}

function rewriteAssetUrl(value, env) {
  if (!value) return value;
  const legacyOrigin = String(env.LEGACY_OSS_PUBLIC_ORIGIN || "").replace(/\/+$/, "");
  const publicOrigin = String(env.PUBLIC_ASSET_ORIGIN || "").replace(/\/+$/, "");
  if (!legacyOrigin || !publicOrigin || !String(value).startsWith(`${legacyOrigin}/`)) return value;
  const objectKey = String(value).slice(legacyOrigin.length + 1);
  const encodedKey = objectKey.split("/").map(segment => encodeURIComponent(segment)).join("/");
  return `${publicOrigin}/media/${encodedKey}`;
}

function assetUrl(objectKey, env) {
  const publicOrigin = String(env.PUBLIC_ASSET_ORIGIN || "").replace(/\/+$/, "");
  const encodedKey = String(objectKey).split("/").map(segment => encodeURIComponent(segment)).join("/");
  return `${publicOrigin}/media/${encodedKey}`;
}

function mapRecommend(row, env) {
  if (!row) return null;
  return {
    id: Number(row.id),
    articleCover: rewriteAssetUrl(row.article_cover, env),
    articleTitle: row.article_title,
    createTime: isoDate(row.create_time)
  };
}

async function listPublicFeed(requestUrl, env, archive) {
  const size = parsePageSize(requestUrl);
  const feed = archive ? "archives" : "articles";
  const cursor = await readCursor(env, requestUrl.searchParams.get("cursor"), feed);
  const columns = archive
    ? "id, article_title, create_time"
    : "id, article_cover, article_title, create_time, is_top, type";
  const parameters = [];
  let cursorClause = "";
  if (cursor) {
    cursorClause = " AND (create_time < ? OR (create_time = ? AND id < ?))";
    parameters.push(cursor.time, cursor.time, Number(cursor.id));
  }
  parameters.push(size + 1);
  const query = `
    SELECT ${columns}
    FROM tb_article
    WHERE is_delete = 0 AND status = 1${cursorClause}
    ORDER BY create_time DESC, id DESC
    LIMIT ?`;
  const response = await env.DB.prepare(query).bind(...parameters).all();
  const rows = response.results || [];
  const hasNext = rows.length > size;
  const items = (hasNext ? rows.slice(0, size) : rows).map(row => archive
    ? { id: Number(row.id), articleTitle: row.article_title, createTime: isoDate(row.create_time) }
    : {
      id: Number(row.id),
      articleCover: rewriteAssetUrl(row.article_cover, env),
      articleTitle: row.article_title,
      createTime: isoDate(row.create_time),
      isTop: Number(row.is_top),
      type: Number(row.type)
    });
  const last = hasNext ? rows[size - 1] : null;
  const nextCursor = last ? await signCursor(env, { feed, ...pageCursor(last) }) : null;
  return jsonResponse(result({ items, nextCursor, hasNext }));
}

async function getPublicArticle(id, env) {
  const responses = await env.DB.batch([
    env.DB.prepare(`
      SELECT a.id, a.article_cover, a.article_title, a.type, a.original_url,
             a.create_time, a.update_time, ca.version AS content_version
      FROM tb_article a
      LEFT JOIN tb_content_asset ca
        ON ca.asset_id = a.content_asset_id AND ca.status = 'ACTIVE'
      WHERE a.id = ? AND a.is_delete = 0 AND a.status = 1`).bind(id),
    env.DB.prepare(`
      SELECT id, article_title, article_cover, create_time
      FROM tb_article
      WHERE id != ? AND is_delete = 0 AND status = 1
      ORDER BY is_top DESC, id DESC LIMIT 6`).bind(id),
    env.DB.prepare(`
      SELECT id, article_title, article_cover, create_time
      FROM tb_article
      WHERE is_delete = 0 AND status = 1
      ORDER BY id DESC LIMIT 5`),
    env.DB.prepare(`
      SELECT id, article_title, article_cover
      FROM tb_article
      WHERE id < ? AND is_delete = 0 AND status = 1
      ORDER BY id DESC LIMIT 1`).bind(id),
    env.DB.prepare(`
      SELECT id, article_title, article_cover
      FROM tb_article
      WHERE id > ? AND is_delete = 0 AND status = 1
      ORDER BY id ASC LIMIT 1`).bind(id)
  ]);
  const article = responses[0].results?.[0];
  if (!article) return failure(NOT_FOUND_CODE, "文章不存在", 404);
  const contentVersion = article.content_version == null ? null : Number(article.content_version);
  return jsonResponse(result({
    id: Number(article.id),
    articleCover: rewriteAssetUrl(article.article_cover, env),
    articleTitle: article.article_title,
    contentVersion,
    contentUrl: contentVersion == null ? null : `/api/articles/${id}/content`,
    type: Number(article.type),
    originalUrl: article.original_url,
    createTime: isoDate(article.create_time),
    updateTime: isoDate(article.update_time),
    lastArticle: mapRecommend(responses[3].results?.[0], env),
    nextArticle: mapRecommend(responses[4].results?.[0], env),
    recommendArticleList: (responses[1].results || []).map(row => mapRecommend(row, env)),
    newestArticleList: (responses[2].results || []).map(row => mapRecommend(row, env))
  }));
}

async function getHomeInfo(env) {
  const responses = await env.DB.batch([
    env.DB.prepare("SELECT COUNT(*) AS count FROM tb_article WHERE is_delete = 0 AND status = 1"),
    env.DB.prepare("SELECT config FROM tb_website_config WHERE id = 1"),
    env.DB.prepare(`
      SELECT id, page_name, page_label, page_cover
      FROM tb_page
      WHERE page_label IN ('home', 'archive', 'about')
      ORDER BY id ASC`)
  ]);
  const rawConfig = responses[1].results?.[0]?.config;
  let websiteConfig = {};
  try {
    websiteConfig = rawConfig ? JSON.parse(rawConfig) : {};
  } catch {
    websiteConfig = {};
  }
  if (websiteConfig.websiteAvatar) websiteConfig.websiteAvatar = rewriteAssetUrl(websiteConfig.websiteAvatar, env);
  return jsonResponse(result({
    articleCount: Number(responses[0].results?.[0]?.count || 0),
    websiteConfig,
    pageList: (responses[2].results || []).map(row => ({
      id: Number(row.id),
      pageName: row.page_name,
      pageLabel: row.page_label,
      pageCover: rewriteAssetUrl(row.page_cover, env)
    }))
  }));
}

async function getAbout(env) {
  const row = await env.DB.prepare("SELECT content FROM tb_about WHERE id = 1").first();
  return jsonResponse(result(row?.content || ""));
}

async function searchArticles(requestUrl, env) {
  const size = parsePageSize(requestUrl);
  const keywords = (requestUrl.searchParams.get("keywords") || "").trim();
  const feed = `search:${keywords}`;
  const cursor = await readCursor(env, requestUrl.searchParams.get("cursor"), feed);
  const offset = cursor ? Number(cursor.offset) : 0;
  const params = [];
  let filter = "";
  if (keywords) {
    filter = " AND article_title LIKE ?";
    params.push(`%${keywords}%`);
  }
  params.push(size + 1, offset);
  const rows = await env.DB.prepare(`
    SELECT id, article_title, is_delete, status
    FROM tb_article
    WHERE is_delete = 0 AND status = 1${filter}
    ORDER BY is_top DESC, id DESC
    LIMIT ? OFFSET ?`).bind(...params).all();
  const candidates = rows.results || [];
  const hasNext = candidates.length > size;
  const items = (hasNext ? candidates.slice(0, size) : candidates).map(row => ({
    id: Number(row.id),
    articleTitle: row.article_title,
    snippet: keywords ? row.article_title : "",
    isDelete: Number(row.is_delete),
    status: Number(row.status)
  }));
  const nextCursor = hasNext ? await signCursor(env, { feed, offset: offset + size }) : null;
  return jsonResponse(result({ items, nextCursor, hasNext }));
}

function parseLastModified(value) {
  if (!value) return 0;
  const normalized = String(value).replace(" ", "T") + "Z";
  const timestamp = Date.parse(normalized);
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function matchesEtag(requestValue, currentEtag) {
  if (!requestValue || !currentEtag) return false;
  const normalizedCurrent = String(currentEtag).trim().replace(/^W\//i, "");
  return String(requestValue).split(",").some(value =>
    value.trim().replace(/^W\//i, "") === normalizedCurrent
  );
}

function contentResponse(body, asset, etag, dataPlane, uploadedAt = null, publicContent = true) {
  const headers = new Headers({
    "Content-Type": asset.content_type || "text/markdown; charset=utf-8",
    "Cache-Control": publicContent ? "public, max-age=60, stale-while-revalidate=300" : "no-store",
    "X-Content-Type-Options": "nosniff",
    "X-Ticastr-Data-Plane": dataPlane
  });
  if (etag) headers.set("ETag", etag);
  if (asset.size_bytes != null) headers.set("Content-Length", String(asset.size_bytes));
  if (uploadedAt) headers.set("Last-Modified", new Date(uploadedAt).toUTCString());
  return new Response(body, { status: 200, headers });
}

async function getArticleContent(request, id, env, publicContent = true) {
  const visibility = publicContent ? " AND a.is_delete = 0 AND a.status = 1" : "";
  const asset = await env.DB.prepare(`
    SELECT ca.object_key, ca.provider, ca.content_type, ca.size_bytes, ca.checksum,
           ca.updated_at
    FROM tb_article a
    JOIN tb_content_asset ca ON ca.asset_id = a.content_asset_id
    WHERE a.id = ?${visibility} AND ca.status = 'ACTIVE'`).bind(id).first();
  if (!asset) return failure(NOT_FOUND_CODE, "文章内容不存在", 404);
  const etag = asset.checksum ? `"${asset.checksum}"` : null;
  const cacheControl = publicContent ? "public, max-age=60, stale-while-revalidate=300" : "no-store";
  if (matchesEtag(request.headers.get("If-None-Match"), etag)) {
    return new Response(null, {
      status: 304,
      headers: {
        ETag: etag,
        "Cache-Control": cacheControl,
        "X-Ticastr-Data-Plane": "d1-content"
      }
    });
  }

  const r2Object = env.ASSETS ? await env.ASSETS.get(asset.object_key) : null;
  if (r2Object) {
    const r2Etag = etag || r2Object.httpEtag || null;
    const r2Asset = {
      ...asset,
      content_type: r2Object.httpMetadata?.contentType || asset.content_type,
      size_bytes: asset.size_bytes ?? r2Object.size
    };
    return contentResponse(r2Object.body, r2Asset, r2Etag, publicContent ? "d1-content-r2" : "d1-content-r2-admin", r2Object.uploaded, publicContent);
  }

  if (asset.provider !== "oss") return failure(50000, "当前内容存储尚未接入 D1 API", 501);
  const origin = String(env.LEGACY_OSS_PUBLIC_ORIGIN || "").replace(/\/+$/, "");
  if (!origin) return failure(50000, "文章内容存储地址未配置", 500);
  const objectUrl = `${origin}/${String(asset.object_key).replace(/^\/+/, "")}`;
  const upstream = await fetch(objectUrl);
  if (!upstream.ok) return failure(50000, "文章内容暂时无法读取", 502);
  const fallbackAsset = {
    ...asset,
    content_type: asset.content_type || upstream.headers.get("Content-Type") || "text/markdown; charset=utf-8"
  };
  const lastModified = parseLastModified(asset.updated_at);
  return contentResponse(upstream.body, fallbackAsset, etag, publicContent ? "d1-content" : "d1-content-admin", lastModified || null, publicContent);
}

async function getMedia(request, env, encodedKey) {
  let objectKey;
  try {
    objectKey = decodeURIComponent(encodedKey);
  } catch {
    return failure(BAD_REQUEST_CODE, "媒体地址无效", 400);
  }
  if (!objectKey || objectKey.startsWith("/") || objectKey.includes("..") || objectKey.includes("\\")) {
    return failure(BAD_REQUEST_CODE, "媒体地址无效", 400);
  }
  const object = env.ASSETS ? await env.ASSETS.get(objectKey) : null;
  if (!object) return failure(NOT_FOUND_CODE, "媒体文件不存在", 404);
  const headers = new Headers({
    "Cache-Control": "public, max-age=86400, stale-while-revalidate=604800",
    "X-Content-Type-Options": "nosniff",
    "X-Ticastr-Data-Plane": "r2-media"
  });
  if (object.httpMetadata?.contentType) headers.set("Content-Type", object.httpMetadata.contentType);
  if (object.httpEtag) headers.set("ETag", object.httpEtag);
  if (object.size != null) headers.set("Content-Length", String(object.size));
  if (object.uploaded) headers.set("Last-Modified", new Date(object.uploaded).toUTCString());
  if (matchesEtag(request.headers.get("If-None-Match"), object.httpEtag)) {
    return new Response(null, { status: 304, headers });
  }
  return new Response(object.body, { status: 200, headers });
}

async function getAdminHome(env) {
  const row = await env.DB.prepare("SELECT COUNT(*) AS count FROM tb_article WHERE is_delete = 0").first();
  return jsonResponse(result({ articleCount: Number(row?.count || 0) }));
}

async function getMenus(env, session) {
  const response = await env.DB.prepare(`
    SELECT DISTINCT m.id, m.name, m.code, m.path, m.component, m.route_key, m.icon,
           m.icon_key, m.section, m.is_hidden, m.order_num, m.parent_id
    FROM tb_user_role ur
    JOIN tb_role_menu rm ON rm.role_id = ur.role_id
    JOIN tb_menu m ON m.id = rm.menu_id
    WHERE ur.user_id = ?
    ORDER BY m.section, m.order_num, m.id`).bind(session.userInfoId).all();
  const rows = (response.results || []).filter(row => !UNSUPPORTED_MENU_CODES.has(row.code));
  const items = new Map(rows.map(row => [Number(row.id), {
    id: Number(row.id),
    name: row.name,
    code: row.code,
    path: row.path,
    component: row.component,
    routeKey: row.route_key,
    icon: row.icon,
    iconKey: row.icon_key,
    section: row.section,
    hidden: Number(row.is_hidden) === 1,
    order: Number(row.order_num),
    children: []
  }]));
  const roots = [];
  for (const row of rows) {
    const item = items.get(Number(row.id));
    const parent = row.parent_id == null ? null : items.get(Number(row.parent_id));
    if (parent) parent.children.push(item);
    else roots.push(item);
  }
  const sort = list => {
    list.sort((left, right) => left.order - right.order || left.id - right.id);
    for (const item of list) {
      if (!item.children.length) delete item.children;
      else sort(item.children);
      delete item.order;
    }
  };
  sort(roots);
  return jsonResponse(result(roots));
}

function adminArticle(row, env) {
  return {
    id: Number(row.id),
    articleCover: rewriteAssetUrl(row.article_cover, env),
    articleTitle: row.article_title,
    type: Number(row.type),
    originalUrl: row.original_url,
    isTop: Number(row.is_top),
    isDelete: Number(row.is_delete),
    status: Number(row.status),
    contentVersion: row.content_version == null ? null : Number(row.content_version),
    createTime: isoDate(row.create_time),
    updateTime: isoDate(row.update_time)
  };
}

async function getAdminArticleList(url, env) {
  const current = readPositiveInteger(url.searchParams.get("current"), 1, "current");
  const size = parsePageSize(url);
  const isDeleteRaw = url.searchParams.get("isDelete");
  const statusRaw = url.searchParams.get("status");
  const typeRaw = url.searchParams.get("type");
  const isDelete = isDeleteRaw == null || isDeleteRaw === "" ? 0 : Number(isDeleteRaw);
  if (![0, 1].includes(isDelete)) throw new HttpError(400, BAD_REQUEST_CODE, "isDelete 参数无效");
  const status = statusRaw == null || statusRaw === "" ? null : Number(statusRaw);
  const type = typeRaw == null || typeRaw === "" ? null : Number(typeRaw);
  if (status != null && ![1, 2, 3].includes(status)) throw new HttpError(400, BAD_REQUEST_CODE, "status 参数无效");
  if (type != null && ![1, 2, 3].includes(type)) throw new HttpError(400, BAD_REQUEST_CODE, "type 参数无效");
  const keywords = (url.searchParams.get("keywords") || "").trim();
  const filters = ["a.is_delete = ?"];
  const params = [isDelete];
  if (keywords) {
    filters.push("a.article_title LIKE ?");
    params.push(`%${keywords}%`);
  }
  if (status != null) {
    filters.push("a.status = ?");
    params.push(status);
  }
  if (type != null) {
    filters.push("a.type = ?");
    params.push(type);
  }
  const where = filters.join(" AND ");
  const offset = (current - 1) * size;
  const responses = await env.DB.batch([
    env.DB.prepare(`SELECT COUNT(*) AS count FROM tb_article a WHERE ${where}`).bind(...params),
    env.DB.prepare(`
      SELECT a.id, a.article_cover, a.article_title, a.type, a.is_top, a.is_delete,
             a.status, a.create_time, a.update_time, ca.version AS content_version
      FROM tb_article a
      LEFT JOIN tb_content_asset ca ON ca.asset_id = a.content_asset_id AND ca.status = 'ACTIVE'
      WHERE ${where}
      ORDER BY a.is_top DESC, a.id DESC
      LIMIT ? OFFSET ?`).bind(...params, size, offset)
  ]);
  return jsonResponse(result({
    recordList: (responses[1].results || []).map(row => adminArticle(row, env)),
    count: Number(responses[0].results?.[0]?.count || 0)
  }));
}

async function getAdminArticle(id, env) {
  const row = await env.DB.prepare(`
    SELECT a.id, a.article_cover, a.article_title, a.type, a.original_url,
           a.is_top, a.is_delete, a.status, a.create_time, a.update_time,
           ca.version AS content_version
    FROM tb_article a
    LEFT JOIN tb_content_asset ca ON ca.asset_id = a.content_asset_id AND ca.status = 'ACTIVE'
    WHERE a.id = ?`).bind(id).first();
  if (!row) return failure(NOT_FOUND_CODE, "文章不存在", 404);
  return jsonResponse(result(adminArticle(row, env)));
}

function optionalString(value, fallback = null) {
  if (value == null) return fallback;
  return String(value);
}

async function saveAdminArticle(request, env, session) {
  const input = await readJson(request);
  const title = String(input.articleTitle || "").trim();
  if (!title) return failure(BAD_REQUEST_CODE, "文章标题不能为空", 400);
  if (title.length > 255) return failure(BAD_REQUEST_CODE, "文章标题不能超过255个字符", 400);
  const type = input.type == null ? 1 : Number(input.type);
  const status = input.status == null ? 3 : Number(input.status);
  const isTop = input.isTop == null ? 0 : Number(input.isTop);
  if (![1, 2, 3].includes(type) || ![1, 2, 3].includes(status) || ![0, 1].includes(isTop)) {
    return failure(BAD_REQUEST_CODE, "文章属性无效", 400);
  }
  const now = nowSql();
  const id = input.id == null || input.id === "" ? null : Number(input.id);
  if (id == null) {
    const insert = await env.DB.prepare(`
      INSERT INTO tb_article
        (user_id, article_cover, article_title, type, original_url, is_top, is_delete, status, create_time, update_time, content_asset_id)
      VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, NULL)`)
      .bind(session.userInfoId, optionalString(input.articleCover, null), title, type,
        optionalString(input.originalUrl, null), isTop, status, now, now).run();
    return jsonResponse(result(Number(insert.meta?.last_row_id || 0)));
  }
  const current = await env.DB.prepare("SELECT id, article_cover, article_title, type, original_url, is_top, status FROM tb_article WHERE id = ?").bind(id).first();
  if (!current) return failure(NOT_FOUND_CODE, "文章不存在", 404);
  const articleCover = Object.prototype.hasOwnProperty.call(input, "articleCover")
    ? optionalString(input.articleCover, null) : current.article_cover;
  const originalUrl = Object.prototype.hasOwnProperty.call(input, "originalUrl")
    ? optionalString(input.originalUrl, null) : current.original_url;
  await env.DB.prepare(`
    UPDATE tb_article
    SET article_cover = ?, article_title = ?, type = ?, original_url = ?, is_top = ?, status = ?, update_time = ?
    WHERE id = ?`)
    .bind(articleCover, title, type, originalUrl, isTop, status, now, id).run();
  return jsonResponse(result(id));
}

async function updateArticleDelete(request, env) {
  const input = await readJson(request);
  const ids = Array.isArray(input.idList) ? input.idList.map(Number).filter(Number.isInteger) : [];
  const isDelete = Number(input.isDelete);
  if (!ids.length || ids.length > 100 || ![0, 1].includes(isDelete)) {
    return failure(BAD_REQUEST_CODE, "删除参数无效", 400);
  }
  const placeholders = ids.map(() => "?").join(",");
  await env.DB.prepare(`UPDATE tb_article SET is_delete = ?, update_time = ? WHERE id IN (${placeholders})`)
    .bind(isDelete, nowSql(), ...ids).run();
  return jsonResponse(result(null));
}

async function updateArticleTop(request, env) {
  const input = await readJson(request);
  const id = Number(input.id);
  const isTop = Number(input.isTop);
  if (!Number.isInteger(id) || ![0, 1].includes(isTop)) return failure(BAD_REQUEST_CODE, "置顶参数无效", 400);
  await env.DB.prepare("UPDATE tb_article SET is_top = ?, update_time = ? WHERE id = ?").bind(isTop, nowSql(), id).run();
  return jsonResponse(result(null));
}

async function deleteArticles(request, env) {
  const input = await readJson(request);
  const ids = Array.isArray(input) ? input.map(Number).filter(Number.isInteger) : [];
  if (!ids.length || ids.length > 100) return failure(BAD_REQUEST_CODE, "文章 ID 列表无效", 400);
  const placeholders = ids.map(() => "?").join(",");
  const assets = await env.DB.prepare(`SELECT object_key, provider FROM tb_content_asset WHERE article_id IN (${placeholders})`).bind(...ids).all();
  await env.DB.batch([
    env.DB.prepare(`UPDATE tb_article SET content_asset_id = NULL WHERE id IN (${placeholders})`).bind(...ids),
    env.DB.prepare(`DELETE FROM tb_content_asset WHERE article_id IN (${placeholders})`).bind(...ids),
    env.DB.prepare(`DELETE FROM tb_article WHERE id IN (${placeholders})`).bind(...ids)
  ]);
  if (env.ASSETS) {
    await Promise.all((assets.results || []).filter(asset => asset.provider === "r2").map(asset => env.ASSETS.delete(asset.object_key)));
  }
  return jsonResponse(result(null));
}

function sanitizeMarkdown(value) {
  if (typeof value !== "string") throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能为空");
  if (!value.trim()) throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能为空");
  let normalized = value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "");
  normalized = normalized.replace(/<!--[\s\S]*?-->|<[^>]*>/gi, "");
  normalized = normalized.replace(/(?:javascript|vbscript|data)\s*:/gi, "");
  if (encoder.encode(normalized).byteLength > MAX_MARKDOWN_BYTES) {
    throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能超过1MiB");
  }
  return normalized;
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

async function trafficSignature(secret, canonical) {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  return Array.from(
    new Uint8Array(await crypto.subtle.sign("HMAC", key, encoder.encode(canonical))),
    byte => byte.toString(16).padStart(2, "0")
  ).join("");
}

function trafficBaseUrl(env) {
  const baseUrl = String(env.XRAY_TRAFFIC_BASE_URL || "").trim().replace(/\/+$/, "");
  const secret = String(env.XRAY_TRAFFIC_SHARED_SECRET || "").trim();
  if (!baseUrl || !secret) throw new HttpError(503, 50300, "代理监控数据源配置不完整", null);
  return { baseUrl, secret };
}

async function trafficBridgeRequest(request, env, endpoint, query = {}, body = undefined, method = request.method) {
  const { baseUrl, secret } = trafficBaseUrl(env);
  const url = new URL(`${baseUrl}/v1/${endpoint}`);
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  const timestamp = Math.floor(Date.now() / 1000);
  const requestTarget = `${url.pathname}${url.search}`;
  const headers = new Headers({
    "Accept": "application/json",
    "X-Ticastr-Traffic-Timestamp": String(timestamp)
  });
  let payload;
  let canonical = `${method}\n${requestTarget}\n${timestamp}`;
  if (method === "POST") {
    payload = JSON.stringify(body || {});
    const digest = await sha256Hex(encoder.encode(payload));
    headers.set("Content-Type", "application/json");
    headers.set("X-Ticastr-Traffic-Body-SHA256", digest);
    canonical += `\n${digest}`;
  }
  headers.set("X-Ticastr-Traffic-Signature", await trafficSignature(secret, canonical));

  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: payload
    });
  } catch {
    throw new HttpError(503, 50300, "代理监控数据源暂时不可用", null);
  }
  let resultBody = null;
  try {
    resultBody = await response.json();
  } catch {
    resultBody = null;
  }
  if (!response.ok) {
    throw new HttpError(503, 50300, "代理监控数据源暂时不可用", null);
  }
  return resultBody;
}

function normalizeTrafficPayload(endpoint, payload) {
  if (endpoint === "overview") {
    return {
      totalConnections: Number(payload?.total_conns || 0),
      totalIps: Number(payload?.total_ips || 0),
      todayConnections: Number(payload?.today_conns || 0),
      totalDomains: Number(payload?.total_domains || 0),
      firstConnection: payload?.first_conn || "",
      lastConnection: payload?.last_conn || "",
      openAlerts: Number(payload?.alerts_open || 0),
      trafficUp: Number(payload?.traffic_up || 0),
      trafficDown: Number(payload?.traffic_down || 0),
      online: Number(payload?.online || 0),
      xray: payload?.xray || "unknown",
      collector: payload?.collector || { at: null, lag: null },
      timezone: payload?.timezone || "Asia/Shanghai",
      generatedAt: Number(payload?.generated_at || 0)
    };
  }
  if (endpoint === "timeseries" || endpoint === "daily") {
    return (Array.isArray(payload) ? payload : []).map(item => ({
      bucket: item.bucket,
      label: item.label,
      connections: Number(item.conns || 0),
      uniqueIps: Number(item.ips || 0),
      up: Number(item.up || 0),
      down: Number(item.down || 0)
    }));
  }
  if (endpoint === "sources") {
    return {
      total: Number(payload?.total || 0),
      items: (Array.isArray(payload?.items) ? payload.items : []).map(item => ({
        ip: item.src,
        connections: Number(item.n || 0),
        targets: Number(item.hosts || 0),
        cc: item.cc,
        prov: item.prov,
        place: item.place,
        org: item.org,
        label: item.label,
        blocked: Number(item.blocked || 0),
        first: item.first,
        last: item.last
      }))
    };
  }
  if (endpoint === "targets") {
    return (Array.isArray(payload) ? payload : []).map(item => ({
      domain: item.host,
      connections: Number(item.n || 0),
      sources: Number(item.ips || 0),
      first: item.first,
      last: item.last
    }));
  }
  if (endpoint === "geo") {
    return {
      countries: (Array.isArray(payload?.countries) ? payload.countries : []).map(item => ({
        code: item.cc,
        connections: Number(item.n || 0),
        sources: Number(item.ips || 0)
      })),
      provinces: (Array.isArray(payload?.provinces) ? payload.provinces : []).map(item => ({
        name: item.prov,
        connections: Number(item.n || 0),
        sources: Number(item.ips || 0)
      })),
      orgs: (Array.isArray(payload?.orgs) ? payload.orgs : []).map(item => ({
        name: item.org,
        connections: Number(item.n || 0)
      }))
    };
  }
  if (endpoint === "live") {
    return (Array.isArray(payload) ? payload : []).map(item => ({
      t: item.t,
      ts: Number(item.ts || 0),
      sourceIp: item.src,
      network: item.net,
      target: item.host,
      port: item.port
    }));
  }
  if (endpoint === "alert-rules" && Array.isArray(payload)) {
    return (Array.isArray(payload) ? payload : []).map(item => ({
      id: item.id,
      enabled: Number(item.enabled || 0),
      metric: item.metric,
      threshold: Number(item.threshold || 0),
      windowSec: Number(item.window_sec || 0),
      cooldownSec: Number(item.cooldown_sec || 0),
      level: item.level,
      email: item.email || ""
    }));
  }
  if (payload && typeof payload === "object" && !Array.isArray(payload)) {
    const normalized = { ...payload };
    if (Object.prototype.hasOwnProperty.call(payload, "runtime_applied")) {
      normalized.runtimeApplied = Boolean(payload.runtime_applied);
    }
    return normalized;
  }
  return payload;
}

function trafficQuery(url, key, fallback, maximum) {
  return readPositiveInteger(url.searchParams.get(key), fallback, key, maximum);
}

async function getTraffic(request, url, env, endpoint) {
  let query = {};
  if (endpoint === "timeseries") query = { hours: trafficQuery(url, "hours", 48, 720) };
  if (endpoint === "daily") query = { days: trafficQuery(url, "days", 30, 90) };
  if (endpoint === "sources") query = {
    days: trafficQuery(url, "days", 30, 90),
    limit: trafficQuery(url, "limit", 100, 500),
    q: String(url.searchParams.get("q") || "").slice(0, 100),
    foreign: url.searchParams.get("foreign") === "1" ? 1 : 0
  };
  if (endpoint === "targets") query = {
    days: trafficQuery(url, "days", 30, 90),
    limit: trafficQuery(url, "limit", 100, 500)
  };
  if (endpoint === "geo") query = { days: trafficQuery(url, "days", 30, 90) };
  if (endpoint === "live") query = { limit: trafficQuery(url, "limit", 150, 500) };
  if (endpoint === "alerts") query = { limit: trafficQuery(url, "limit", 100, 500) };
  const payload = await trafficBridgeRequest(request, env, endpoint, query);
  return jsonResponse(result(normalizeTrafficPayload(endpoint, payload)));
}

async function postTraffic(request, url, env, endpoint) {
  const input = await readJson(request);
  const bridgeInput = endpoint === "alert-rules"
    ? {
        ...input,
        window_sec: input.window_sec ?? input.windowSec,
        cooldown_sec: input.cooldown_sec ?? input.cooldownSec
      }
    : input;
  const payload = await trafficBridgeRequest(request, env, endpoint, {}, bridgeInput);
  return jsonResponse(result(normalizeTrafficPayload(endpoint, payload)));
}

async function dispatchTraffic(request, url, env) {
  const suffix = url.pathname.slice("/admin/traffic/".length);
  if (request.method === "GET" && [
    "overview", "timeseries", "daily", "sources", "targets", "geo", "live", "alerts",
    "blocklist", "alert-rules"
  ].includes(suffix)) return getTraffic(request, url, env, suffix);
  if (request.method === "POST" && [
    "block", "unblock", "label", "alerts/ack", "alert-rules", "blocklist/sync", "collect", "geo/refresh"
  ].includes(suffix)) return postTraffic(request, url, env, suffix);
  const deleteRule = suffix.match(/^alert-rules\/(.+)$/);
  if (request.method === "DELETE" && deleteRule) {
    const ruleId = decodeURIComponent(deleteRule[1]);
    if (!ruleId || ruleId.length > 64) return failure(BAD_REQUEST_CODE, "规则 ID 无效", 400);
    const payload = await trafficBridgeRequest(request, env, "alert-rules/delete", {}, { id: ruleId }, "POST");
    return jsonResponse(result(normalizeTrafficPayload("alert-rules/delete", payload)));
  }
  return failure(NOT_FOUND_CODE, "接口不存在", 404);
}

async function currentContentAsset(id, env) {
  return env.DB.prepare(`
    SELECT asset_id, article_id, provider, object_key, content_type, format, version,
           checksum, size_bytes, status, created_at, updated_at
    FROM tb_content_asset
    WHERE article_id = ? AND status = 'ACTIVE'
    ORDER BY version DESC LIMIT 1`).bind(id).first();
}

function contentResult(id, version, contentType, sizeBytes, checksum) {
  return {
    articleId: Number(id),
    version: Number(version),
    contentType,
    sizeBytes: Number(sizeBytes),
    checksum,
    lastModified: new Date().toISOString(),
    contentUrl: `/api/articles/${id}/content`
  };
}

async function replaceArticleContent(env, id, markdown, expectedVersion) {
  const article = await env.DB.prepare("SELECT id FROM tb_article WHERE id = ?").bind(id).first();
  if (!article) throw new HttpError(404, NOT_FOUND_CODE, "文章不存在");
  const current = await currentContentAsset(id, env);
  const currentVersion = current ? Number(current.version) : null;
  if (expectedVersion != null && Number(expectedVersion) !== currentVersion) {
    throw new HttpError(409, CONFLICT_CODE, "文章内容已被其他修改，请先重新加载", { currentVersion });
  }
  if (!env.ASSETS) throw new HttpError(501, NOT_IMPLEMENTED_CODE, "R2 内容存储未配置");
  const bytes = encoder.encode(markdown);
  const checksum = await sha256Hex(bytes);
  const version = currentVersion == null ? 1 : currentVersion + 1;
  const assetId = `r2-content:${crypto.randomUUID()}`;
  const objectKey = `articles/${id}/${crypto.randomUUID()}.md`;
  const now = nowSql();
  await env.ASSETS.put(objectKey, bytes, {
    httpMetadata: { contentType: "text/markdown; charset=utf-8" },
    customMetadata: { articleId: String(id), version: String(version), checksum }
  });
  try {
    const statements = [];
    if (current) {
      statements.push(env.DB.prepare(`
        UPDATE tb_content_asset
        SET status = 'DELETED', deleted_at = ?, updated_at = ?
        WHERE asset_id = ?`).bind(now, now, current.asset_id));
    }
    statements.push(env.DB.prepare(`
      INSERT INTO tb_content_asset
        (asset_id, article_id, provider, storage_config_id, object_key, content_type, format,
         version, checksum, size_bytes, status, created_at, updated_at, deleted_at, last_error)
      VALUES (?, ?, 'r2', NULL, ?, 'text/markdown; charset=utf-8', 'markdown', ?, ?, ?, 'ACTIVE', ?, ?, NULL, NULL)`)
      .bind(assetId, id, objectKey, version, checksum, bytes.byteLength, now, now));
    statements.push(env.DB.prepare("UPDATE tb_article SET content_asset_id = ?, update_time = ? WHERE id = ?").bind(assetId, now, id));
    await env.DB.batch(statements);
  } catch (error) {
    await env.ASSETS.delete(objectKey);
    throw error;
  }
  return contentResult(id, version, "text/markdown; charset=utf-8", bytes.byteLength, checksum);
}

async function saveAdminArticleContent(request, env, id) {
  const input = await readJson(request, MAX_JSON_BODY_BYTES);
  const markdown = sanitizeMarkdown(input.content);
  const expectedVersion = input.expectedVersion == null || input.expectedVersion === ""
    ? null : Number(input.expectedVersion);
  if (expectedVersion != null && !Number.isInteger(expectedVersion)) {
    return failure(BAD_REQUEST_CODE, "expectedVersion 参数无效", 400);
  }
  const saved = await replaceArticleContent(env, id, markdown, expectedVersion);
  return jsonResponse(result(saved));
}

async function readContentBytes(asset, env) {
  let bytes;
  const r2Object = env.ASSETS ? await env.ASSETS.get(asset.object_key) : null;
  if (r2Object) {
    if (r2Object.size > MAX_MARKDOWN_BYTES) throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能超过1MiB");
    bytes = new Uint8Array(await r2Object.arrayBuffer());
  } else {
    if (asset.provider !== "oss") throw new HttpError(501, NOT_IMPLEMENTED_CODE, "历史内容存储尚未接入 D1 API");
    const origin = String(env.LEGACY_OSS_PUBLIC_ORIGIN || "").replace(/\/+$/, "");
    const upstream = await fetch(`${origin}/${String(asset.object_key).replace(/^\/+/, "")}`);
    if (!upstream.ok) throw new HttpError(502, 50000, "文章内容版本读取失败");
    const contentLength = Number(upstream.headers.get("Content-Length"));
    if (Number.isFinite(contentLength) && contentLength > MAX_MARKDOWN_BYTES) {
      throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能超过1MiB");
    }
    bytes = new Uint8Array(await upstream.arrayBuffer());
  }
  if (bytes.byteLength > MAX_MARKDOWN_BYTES) throw new HttpError(400, BAD_REQUEST_CODE, "文章内容不能超过1MiB");
  return bytes;
}

async function listContentVersions(url, env, id) {
  const size = parsePageSize(url, 20);
  const feed = `content:${id}`;
  const cursor = await readCursor(env, url.searchParams.get("cursor"), feed);
  const params = [id];
  let cursorClause = "";
  if (cursor) {
    cursorClause = " AND version < ?";
    params.push(Number(cursor.version));
  }
  params.push(size + 1);
  const rows = await env.DB.prepare(`
    SELECT asset_id, article_id, version, content_type, size_bytes, checksum, status, created_at, updated_at
    FROM tb_content_asset
    WHERE article_id = ?${cursorClause}
    ORDER BY version DESC
    LIMIT ?`).bind(...params).all();
  const candidates = rows.results || [];
  const hasNext = candidates.length > size;
  const items = (hasNext ? candidates.slice(0, size) : candidates).map(row => ({
    assetId: row.asset_id,
    articleId: Number(row.article_id),
    version: Number(row.version),
    contentType: row.content_type,
    sizeBytes: Number(row.size_bytes || 0),
    checksum: row.checksum,
    status: row.status,
    createdAt: isoDate(row.created_at),
    updatedAt: isoDate(row.updated_at)
  }));
  const last = hasNext ? items[items.length - 1] : null;
  const nextCursor = last ? await signCursor(env, { feed, version: last.version }) : null;
  return jsonResponse(result({ items, nextCursor, hasNext }));
}

async function restoreContentVersion(request, env, id, version) {
  const source = await env.DB.prepare(`
    SELECT asset_id, article_id, provider, object_key, content_type, version, checksum, size_bytes, status
    FROM tb_content_asset
    WHERE article_id = ? AND version = ?`).bind(id, version).first();
  if (!source || source.status === "DELETED") return failure(NOT_FOUND_CODE, "文章内容版本不存在", 404);
  const input = await readJson(request);
  const expectedVersion = input.expectedVersion == null || input.expectedVersion === ""
    ? null : Number(input.expectedVersion);
  if (expectedVersion != null && !Number.isInteger(expectedVersion)) return failure(BAD_REQUEST_CODE, "expectedVersion 参数无效", 400);
  const bytes = await readContentBytes(source, env);
  const markdown = sanitizeMarkdown(decoder.decode(bytes));
  const saved = await replaceArticleContent(env, id, markdown, expectedVersion);
  return jsonResponse(result(saved));
}

async function getAdminPages(env) {
  const rows = await env.DB.prepare(`
    SELECT id, page_name, page_label, page_cover
    FROM tb_page
    ORDER BY id ASC`).all();
  return jsonResponse(result((rows.results || []).map(row => ({
    id: Number(row.id),
    pageName: row.page_name,
    pageLabel: row.page_label,
    pageCover: rewriteAssetUrl(row.page_cover, env)
  }))));
}

async function savePage(request, env) {
  const input = await readJson(request);
  const pageName = String(input.pageName || "").trim();
  const pageLabel = String(input.pageLabel || "").trim();
  const pageCover = String(input.pageCover || "").trim();
  if (!pageName || !pageCover || !["home", "archive", "about"].includes(pageLabel)) {
    return failure(BAD_REQUEST_CODE, "页面参数无效", 400);
  }
  const now = nowSql();
  const id = input.id == null || input.id === "" ? null : Number(input.id);
  if (id == null) {
    await env.DB.prepare(`
      INSERT INTO tb_page (page_name, page_label, page_cover, create_time, update_time)
      VALUES (?, ?, ?, ?, ?)`).bind(pageName, pageLabel, pageCover, now, now).run();
  } else {
    const updated = await env.DB.prepare(`
      UPDATE tb_page SET page_name = ?, page_label = ?, page_cover = ?, update_time = ? WHERE id = ?`)
      .bind(pageName, pageLabel, pageCover, now, id).run();
    if (!updated.meta?.changes) return failure(NOT_FOUND_CODE, "页面不存在", 404);
  }
  return jsonResponse(result(null));
}

function normalizeWebsiteConfig(input) {
  const config = {};
  for (const key of WEBSITE_CONFIG_KEYS) {
    if (key === "socialUrlList") {
      config[key] = Array.isArray(input?.[key]) ? input[key].map(value => String(value)).filter(Boolean) : [];
    } else {
      config[key] = input?.[key] == null ? "" : String(input[key]);
    }
  }
  return config;
}

async function getWebsiteConfig(env) {
  const row = await env.DB.prepare("SELECT config FROM tb_website_config WHERE id = 1").first();
  let config = {};
  try {
    config = row?.config ? normalizeWebsiteConfig(JSON.parse(row.config)) : normalizeWebsiteConfig({});
  } catch {
    config = normalizeWebsiteConfig({});
  }
  if (config.websiteAvatar) config.websiteAvatar = rewriteAssetUrl(config.websiteAvatar, env);
  return jsonResponse(result(config));
}

async function saveWebsiteConfig(request, env) {
  const config = normalizeWebsiteConfig(await readJson(request));
  const now = nowSql();
  const existing = await env.DB.prepare("SELECT id FROM tb_website_config WHERE id = 1").first();
  if (existing) {
    await env.DB.prepare("UPDATE tb_website_config SET config = ?, update_time = ? WHERE id = 1").bind(JSON.stringify(config), now).run();
  } else {
    await env.DB.prepare("INSERT INTO tb_website_config (id, config, create_time, update_time) VALUES (1, ?, ?, ?)").bind(JSON.stringify(config), now, now).run();
  }
  return jsonResponse(result(null));
}

async function saveAbout(request, env) {
  const input = await readJson(request);
  if (typeof input.aboutContent !== "string") return failure(BAD_REQUEST_CODE, "关于我内容不能为空", 400);
  const now = nowSql();
  const existing = await env.DB.prepare("SELECT id FROM tb_about WHERE id = 1").first();
  if (existing) await env.DB.prepare("UPDATE tb_about SET content = ?, update_time = ? WHERE id = 1").bind(input.aboutContent, now).run();
  else await env.DB.prepare("INSERT INTO tb_about (id, content, create_time, update_time) VALUES (1, ?, ?, ?)").bind(input.aboutContent, now, now).run();
  return jsonResponse(result(null));
}

async function updateUserInfo(request, env, session) {
  const input = await readJson(request);
  const nickname = String(input.nickname || "").trim();
  if (!nickname) return failure(BAD_REQUEST_CODE, "昵称不能为空", 400);
  await env.DB.prepare(`
    UPDATE tb_user_info SET nickname = ?, intro = ?, web_site = ?, update_time = ? WHERE id = ?`)
    .bind(nickname, optionalString(input.intro, null), optionalString(input.webSite, null), nowSql(), session.userInfoId).run();
  return jsonResponse(result(null));
}

async function updatePassword(request, env, session) {
  const input = await readJson(request);
  const oldPassword = String(input.oldPassword || "");
  const newPassword = String(input.newPassword || "");
  if (!oldPassword || newPassword.length < 6) return failure(BAD_REQUEST_CODE, "新密码不能少于6位", 400);
  const row = await env.DB.prepare("SELECT password FROM tb_user_auth WHERE id = ? AND user_info_id = ?").bind(session.authId, session.userInfoId).first();
  if (!row || !(await bcrypt.compare(oldPassword, row.password))) return failure(BAD_REQUEST_CODE, "旧密码不正确", 200);
  const password = await bcrypt.hash(newPassword, 12);
  await env.DB.prepare("UPDATE tb_user_auth SET password = ?, update_time = ? WHERE id = ?").bind(password, nowSql(), session.authId).run();
  return jsonResponse(result(null));
}

function fileExtension(file) {
  const name = String(file.name || "");
  const match = name.match(/\.([a-z0-9]{1,10})$/i);
  if (match) return match[1].toLowerCase();
  const type = String(file.type || "").toLowerCase();
  return ({
    "image/jpeg": "jpg", "image/png": "png", "image/gif": "gif", "image/webp": "webp",
    "image/svg+xml": "svg", "image/avif": "avif"
  })[type] || "bin";
}

async function uploadMedia(request, env, session, kind) {
  const contentLength = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(contentLength) && contentLength > MAX_UPLOAD_BYTES + 4096) {
    throw new HttpError(413, BAD_REQUEST_CODE, "图片不能超过20MiB");
  }
  const form = await request.formData();
  const file = form.get("file");
  if (!file || typeof file.arrayBuffer !== "function") return failure(BAD_REQUEST_CODE, "请选择文件", 400);
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (!bytes.byteLength || bytes.byteLength > MAX_UPLOAD_BYTES) return failure(BAD_REQUEST_CODE, "图片不能超过20MiB", 400);
  if (!env.ASSETS) throw new HttpError(501, NOT_IMPLEMENTED_CODE, "R2 媒体存储未配置");
  const date = new Date();
  const month = `${date.getUTCFullYear()}/${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
  const objectKey = `media/${month}/${crypto.randomUUID()}.${fileExtension(file)}`;
  const contentType = String(file.type || "application/octet-stream");
  const url = assetUrl(objectKey, env);
  await env.ASSETS.put(objectKey, bytes, { httpMetadata: { contentType } });
  try {
    const now = nowSql();
    await env.DB.prepare(`
      INSERT INTO tb_media_asset
        (asset_id, asset_reference, object_key, storage_mode, storage_config_id, status, created_at, updated_at, deleted_at, last_error)
      VALUES (?, ?, ?, 'r2', NULL, 'ACTIVE', ?, ?, NULL, NULL)`)
      .bind(`r2-media:${crypto.randomUUID()}`, url, objectKey, now, now).run();
    if (kind === "avatar") {
      await env.DB.prepare("UPDATE tb_user_info SET avatar = ?, update_time = ? WHERE id = ?")
        .bind(url, now, session.userInfoId).run();
    }
  } catch (error) {
    await env.ASSETS.delete(objectKey);
    throw error;
  }
  return jsonResponse(result(url));
}

function isProtectedPath(pathname) {
  return pathname === "/admin" || pathname.startsWith("/admin/") || pathname === "/users/info" || pathname === "/users/avatar";
}

function notImplemented(message = "该管理功能将在下一阶段接入") {
  return failure(NOT_IMPLEMENTED_CODE, message, 501);
}

async function dispatch(request, env) {
  const url = new URL(request.url);
  if (url.pathname === "/_d1-health") {
    const row = await env.DB.prepare("SELECT COUNT(*) AS count FROM tb_article").first();
    return jsonResponse(result({
      environment: env.ENVIRONMENT || "unknown",
      database: "d1",
      articleCount: Number(row?.count || 0),
      sessionStore: env.KV ? "kv" : "unconfigured"
    }));
  }
  if (url.pathname === "/login" && request.method === "POST") return login(request, env);
  if (url.pathname === "/logout" && request.method === "POST") return logout(request, env);

  if (isProtectedPath(url.pathname)) {
    const session = await readSession(request, env);
    if (!session) return failure(UNAUTHORIZED_CODE, "登录状态已失效，请重新登录", 401);
    if (isMutatingMethod(request.method)) {
      const headerToken = request.headers.get("X-XSRF-TOKEN");
      const cookieToken = cookieValue(request, CSRF_COOKIE);
      if (!constantTimeEqual(headerToken, session.csrf) || !constantTimeEqual(cookieToken, session.csrf)) {
        return failure(FORBIDDEN_CODE, "CSRF 校验失败，请刷新页面后重试", 403);
      }
    }
    if (!(await hasPermission(env, session, url.pathname, request.method))) {
      return failure(FORBIDDEN_CODE, "没有权限访问该接口", 403);
    }

    const articleContent = url.pathname.match(/^\/admin\/articles\/(\d+)\/content$/);
    if (articleContent) {
      if (request.method === "GET") return getArticleContent(request, Number(articleContent[1]), env, false);
      if (request.method === "PUT") return saveAdminArticleContent(request, env, Number(articleContent[1]));
    }
    const articleVersionRestore = url.pathname.match(/^\/admin\/articles\/(\d+)\/versions\/(\d+)\/restore$/);
    if (articleVersionRestore && request.method === "POST") {
      return restoreContentVersion(request, env, Number(articleVersionRestore[1]), Number(articleVersionRestore[2]));
    }
    const articleVersions = url.pathname.match(/^\/admin\/articles\/(\d+)\/versions$/);
    if (articleVersions && request.method === "GET") return listContentVersions(url, env, Number(articleVersions[1]));
    const articleDetail = url.pathname.match(/^\/admin\/articles\/(\d+)$/);
    if (articleDetail && request.method === "GET") return getAdminArticle(Number(articleDetail[1]), env);
    if (url.pathname === "/admin/articles" && request.method === "GET") return getAdminArticleList(url, env);
    if (url.pathname === "/admin/articles" && request.method === "POST") return saveAdminArticle(request, env, session);
    if (url.pathname === "/admin/articles" && request.method === "PUT") return updateArticleDelete(request, env);
    if (url.pathname === "/admin/articles" && request.method === "DELETE") return deleteArticles(request, env);
    if (url.pathname === "/admin/articles/top" && request.method === "PUT") return updateArticleTop(request, env);
    if (url.pathname === "/admin/articles/images" && request.method === "POST") return uploadMedia(request, env, session, "article");
    if (url.pathname === "/admin" && request.method === "GET") return getAdminHome(env);
    if (url.pathname === "/admin/user/menus" && request.method === "GET") return getMenus(env, session);
    if (url.pathname === "/admin/pages" && request.method === "GET") return getAdminPages(env);
    if (url.pathname === "/admin/pages" && request.method === "POST") return savePage(request, env);
    if (url.pathname === "/admin/website/config" && request.method === "GET") return getWebsiteConfig(env);
    if (url.pathname === "/admin/website/config" && request.method === "PUT") return saveWebsiteConfig(request, env);
    if (url.pathname === "/admin/about" && request.method === "PUT") return saveAbout(request, env);
    if (url.pathname === "/admin/config/images" && request.method === "POST") return uploadMedia(request, env, session, "config");
    if (url.pathname === "/admin/users/password" && request.method === "PUT") return updatePassword(request, env, session);
    if (url.pathname === "/users/info" && request.method === "PUT") return updateUserInfo(request, env, session);
    if (url.pathname === "/users/avatar" && request.method === "POST") return uploadMedia(request, env, session, "avatar");
    if (url.pathname.startsWith("/admin/traffic/")) return dispatchTraffic(request, url, env);
    if (url.pathname.startsWith("/admin/storage/")) {
      return notImplemented("存储管理仍由原 API 提供");
    }
    return failure(NOT_FOUND_CODE, "接口不存在", 404);
  }

  if (request.method !== "GET") return failure(40500, "仅支持 GET 请求", 405);
  if (url.pathname === "/") return getHomeInfo(env);
  if (url.pathname === "/about") return getAbout(env);
  if (url.pathname === "/articles") return listPublicFeed(url, env, false);
  if (url.pathname === "/articles/archives") return listPublicFeed(url, env, true);
  if (url.pathname === "/articles/search") return searchArticles(url, env);
  const mediaMatch = url.pathname.match(/^\/media\/(.+)$/);
  if (mediaMatch) return getMedia(request, env, mediaMatch[1]);
  const contentMatch = url.pathname.match(/^\/articles\/(\d+)\/content$/);
  if (contentMatch) return getArticleContent(request, Number(contentMatch[1]), env, true);
  const articleMatch = url.pathname.match(/^\/articles\/(\d+)$/);
  if (articleMatch) return getPublicArticle(Number(articleMatch[1]), env);
  return failure(NOT_FOUND_CODE, "接口不存在", 404);
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }), request);
    }
    try {
      return withCors(await dispatch(request, env), request);
    } catch (error) {
      console.error(JSON.stringify({ event: "d1_api_error", message: error?.message || String(error) }));
      if (error instanceof HttpError) {
        return withCors(failure(error.code, error.message, error.status, error.data), request);
      }
      if (error?.message?.includes("游标")) return withCors(failure(BAD_REQUEST_CODE, error.message, 400), request);
      return withCors(failure(50000, "系统异常，请稍后重试", 500), request);
    }
  }
};
