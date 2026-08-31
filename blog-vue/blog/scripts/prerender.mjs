import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import MarkdownIt from "markdown-it";
import { articleSeo, staticSeoTags, escapeHtml } from "../src/utils/seo.js";
import { listPublicArticles, validateOrigin } from "./generate-site-feeds.mjs";

const distDirectory = path.resolve(process.env.DIST_DIR || "dist");
const apiOrigin = (process.env.PRERENDER_API_URL || "http://localhost:8090").replace(/\/+$/, "");
const publicOrigin = validateOrigin(process.env.PUBLIC_SITE_ORIGIN || "http://localhost:8080");
const maxContentBytes = 256 * 1024;
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true, typographer: true });

function unwrap(value) {
  return value?.data ?? value;
}

async function getJson(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error(`SEO metadata request failed: ${response.status} ${url}`);
  return unwrap(await response.json());
}

async function getMarkdown(url) {
  const response = await fetch(url, { headers: { Accept: "text/markdown" } });
  if (!response.ok) throw new Error(`SEO content request failed: ${response.status} ${url}`);
  if (!response.body) throw new Error(`SEO content response has no body: ${url}`);
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxContentBytes) throw new Error("SEO article content exceeds the prerender limit");
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  return new TextDecoder().decode(Buffer.concat(chunks.map(chunk => Buffer.from(chunk))));
}

function sanitizeRenderedHtml(value) {
  return value
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "")
    .replace(/\s(?:href|src)\s*=\s*(["'])\s*javascript:[^"']*\1/gi, "");
}

export function renderArticleShell(metadata, content) {
  const seo = articleSeo(metadata, {
    origin: publicOrigin,
    siteName: "Ticastr",
    siteDescription: "记录生活，分享技术"
  });
  const body = sanitizeRenderedHtml(markdown.render(content));
  const title = escapeHtml(metadata.articleTitle || "Ticastr");
  const date = metadata.updateTime || metadata.createTime || "";
  return `<main id="prerendered-article"><article class="article-wrapper"><header><h1>${title}</h1>${date ? `<time datetime="${escapeHtml(date)}">${escapeHtml(date)}</time>` : ""}</header><div class="article-content markdown-body">${body}</div></article></main>`;
}

export async function prerenderArticles() {
  const shell = await fs.readFile(path.join(distDirectory, "index.html"), "utf8");
  if (!shell.includes("<div id=\"app\"></div>")) throw new Error("dist/index.html is missing the app mount");
  const articles = await listPublicArticles();
  for (const listed of articles) {
    const metadata = await getJson(`${apiOrigin}/articles/${encodeURIComponent(listed.id)}`);
    if (!metadata || metadata.id == null || metadata.status != null && metadata.status !== 1 || metadata.isDelete === 1) {
      throw new Error(`Refusing to prerender non-public article ${listed.id}`);
    }
    const content = await getMarkdown(`${apiOrigin}/articles/${encodeURIComponent(listed.id)}/content`);
    if (!content.trim()) throw new Error(`Refusing to prerender empty article ${listed.id}`);
    const seo = articleSeo(metadata, { origin: publicOrigin, siteName: "Ticastr" });
    const head = staticSeoTags(seo);
    const html = shell
      .replace(/<title>[\s\S]*?<\/title>/i, "")
      .replace("</head>", `${head}</head>`)
      .replace('<div id="app"></div>', `<div id="app" data-prerendered="true">${renderArticleShell(metadata, content)}</div>`);
    const output = path.join(distDirectory, "articles", String(listed.id), "index.html");
    await fs.mkdir(path.dirname(output), { recursive: true });
    await fs.writeFile(output, html, "utf8");
  }
  return articles.length;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  prerenderArticles().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
