import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const distDirectory = path.resolve(process.env.DIST_DIR || "dist");
const apiOrigin = (process.env.PRERENDER_API_URL || "http://localhost:8090").replace(/\/+$/, "");
const publicOrigin = validateOrigin(process.env.PUBLIC_SITE_ORIGIN || "http://localhost:8080");

export function validateOrigin(value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error("PUBLIC_SITE_ORIGIN must be an absolute URL");
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== "/") {
    throw new Error("PUBLIC_SITE_ORIGIN must be an origin without credentials, path, query, or hash");
  }
  if (process.env.NODE_ENV === "production" && parsed.protocol !== "https:") {
    throw new Error("PUBLIC_SITE_ORIGIN must use HTTPS in production");
  }
  return parsed.toString().replace(/\/+$/, "");
}

function xml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function articleDescription(article) {
  return String(article?.articleSummary || article?.summary || article?.articleIntro || "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 240);
}

function unwrap(value) {
  return value?.data ?? value;
}

async function getJson(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`SEO data request failed: ${response.status} ${url}`);
  return unwrap(await response.json());
}

export async function listPublicArticles() {
  const result = [];
  let cursor = "";
  for (let page = 0; page < 200; page += 1) {
    const params = new URLSearchParams({ size: "50" });
    if (cursor) params.set("cursor", cursor);
    const payload = await getJson(`${apiOrigin}/articles/archives?${params}`);
    const items = Array.isArray(payload?.items) ? payload.items : [];
    result.push(...items.filter(item => item?.id != null));
    if (!payload?.hasNext || !payload?.nextCursor || items.length === 0) break;
    cursor = payload.nextCursor;
  }
  return result;
}

export function buildRss(articles) {
  const items = articles.slice(0, 50).map(article => {
    const url = `${publicOrigin}/articles/${encodeURIComponent(article.id)}/`;
    const date = article.updateTime || article.createTime;
    return [
      "<item>",
      `<title>${xml(article.articleTitle)}</title>`,
      `<link>${xml(url)}</link>`,
      `<guid isPermaLink=\"true\">${xml(url)}</guid>`,
      date ? `<pubDate>${xml(new Date(date).toUTCString())}</pubDate>` : "",
      `<description>${xml(articleDescription(article))}</description>`,
      "</item>"
    ].filter(Boolean).join("");
  }).join("");
  return `<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>${xml(publicOrigin)}</title><link>${xml(publicOrigin + "/")}</link><description>公开文章订阅</description>${items}</channel></rss>`;
}

export function buildAtom(articles) {
  const entries = articles.slice(0, 50).map(article => {
    const url = `${publicOrigin}/articles/${encodeURIComponent(article.id)}/`;
    const date = article.updateTime || article.createTime || new Date().toISOString();
    return `<entry><title>${xml(article.articleTitle)}</title><id>${xml(url)}</id><link href="${xml(url)}"/><updated>${xml(new Date(date).toISOString())}</updated><summary>${xml(articleDescription(article))}</summary></entry>`;
  }).join("");
  return `<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom"><title>${xml(publicOrigin)}</title><id>${xml(publicOrigin + "/")}</id><link href="${xml(publicOrigin + "/")}"/>${entries}</feed>`;
}

export async function generateFeeds() {
  const articles = await listPublicArticles();
  await fs.mkdir(distDirectory, { recursive: true });
  const urls = articles.map(article => `${publicOrigin}/articles/${encodeURIComponent(article.id)}/`);
  const sitemap = `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>${xml(publicOrigin + "/")}</loc></url>${urls.map(url => `<url><loc>${xml(url)}</loc></url>`).join("")}</urlset>`;
  const robots = `User-agent: *\nAllow: /\nDisallow: /admin\nSitemap: ${publicOrigin}/sitemap.xml\n`;
  await Promise.all([
    fs.writeFile(path.join(distDirectory, "sitemap.xml"), sitemap, "utf8"),
    fs.writeFile(path.join(distDirectory, "robots.txt"), robots, "utf8"),
    fs.writeFile(path.join(distDirectory, "feed.xml"), buildRss(articles), "utf8"),
    fs.writeFile(path.join(distDirectory, "rss.xml"), buildRss(articles), "utf8"),
    fs.writeFile(path.join(distDirectory, "atom.xml"), buildAtom(articles), "utf8")
  ]);
  return articles;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  generateFeeds().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
