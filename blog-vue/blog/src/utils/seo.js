const MANAGED_ATTRIBUTE = "data-ticastr-seo";

function text(value, fallback = "") {
  return String(value == null ? fallback : value).replace(/\s+/g, " ").trim();
}

export function escapeHtml(value) {
  return text(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function absoluteUrl(path, origin = "") {
  const base = text(origin || (typeof window !== "undefined" ? window.location.origin : ""));
  try {
    return new URL(path || "/", base || "http://localhost").toString();
  } catch {
    return path || "/";
  }
}

export function articleSeo(metadata, options = {}) {
  const id = metadata?.id ?? metadata?.articleId;
  const title = text(metadata?.articleTitle, options.siteName || "Ticastr");
  const description = text(
    metadata?.articleSummary || metadata?.summary || metadata?.articleIntro,
    options.siteDescription || "记录生活，分享技术"
  ).slice(0, options.descriptionMaxLength || 180);
  const canonical = absoluteUrl(id ? `/articles/${encodeURIComponent(id)}/` : "/", options.origin);
  const image = metadata?.articleCover ? absoluteUrl(metadata.articleCover, options.origin) : "";
  const published = metadata?.createTime || metadata?.createdAt || undefined;
  const modified = metadata?.updateTime || metadata?.updatedAt || published;
  const author = text(metadata?.authorName || metadata?.nickname || options.author, "博客作者");
  return {
    title,
    description,
    canonical,
    image,
    og: { title, description, url: canonical, type: id ? "article" : "website", image },
    twitter: { card: image ? "summary_large_image" : "summary", title, description, image },
    jsonLd: {
      "@context": "https://schema.org",
      "@type": id ? "Article" : "WebSite",
      headline: title,
      datePublished: published,
      dateModified: modified,
      author: { "@type": "Person", name: author },
      ...(image ? { image: [image] } : {}),
      mainEntityOfPage: { "@type": "WebPage", "@id": canonical }
    }
  };
}

function addMeta(name, content, property = false) {
  if (!content) return;
  const element = document.createElement("meta");
  element.setAttribute(property ? "property" : "name", name);
  element.setAttribute("content", content);
  element.setAttribute(MANAGED_ATTRIBUTE, "true");
  document.head.appendChild(element);
}

export function clearSeoHead() {
  document.head.querySelectorAll(`[${MANAGED_ATTRIBUTE}]`).forEach(element => element.remove());
}

export function applySeo(metadata, options = {}) {
  if (typeof document === "undefined") return articleSeo(metadata, options);
  const seo = articleSeo(metadata, options);
  clearSeoHead();
  document.title = seo.title;
  addMeta("description", seo.description);
  addMeta("og:title", seo.og.title, true);
  addMeta("og:description", seo.og.description, true);
  addMeta("og:url", seo.og.url, true);
  addMeta("og:type", seo.og.type, true);
  addMeta("og:image", seo.og.image, true);
  addMeta("twitter:card", seo.twitter.card);
  addMeta("twitter:title", seo.twitter.title);
  addMeta("twitter:description", seo.twitter.description);
  addMeta("twitter:image", seo.twitter.image);
  const link = document.createElement("link");
  link.rel = "canonical";
  link.href = seo.canonical;
  link.setAttribute(MANAGED_ATTRIBUTE, "true");
  document.head.appendChild(link);
  const script = document.createElement("script");
  script.type = "application/ld+json";
  script.setAttribute(MANAGED_ATTRIBUTE, "true");
  // Prevent a JSON-LD value from closing the script element in HTML.
  script.textContent = JSON.stringify(seo.jsonLd).replace(/</g, "\\u003c");
  document.head.appendChild(script);
  return seo;
}

export function staticSeoTags(seo) {
  return [
    `<title>${escapeHtml(seo.title)}</title>`,
    `<meta name="description" content="${escapeHtml(seo.description)}">`,
    `<link rel="canonical" href="${escapeHtml(seo.canonical)}">`,
    `<meta property="og:title" content="${escapeHtml(seo.og.title)}">`,
    `<meta property="og:description" content="${escapeHtml(seo.og.description)}">`,
    `<meta property="og:url" content="${escapeHtml(seo.og.url)}">`,
    `<meta property="og:type" content="${escapeHtml(seo.og.type)}">`,
    seo.og.image ? `<meta property="og:image" content="${escapeHtml(seo.og.image)}">` : "",
    `<meta name="twitter:card" content="${escapeHtml(seo.twitter.card)}">`,
    `<meta name="twitter:title" content="${escapeHtml(seo.twitter.title)}">`,
    `<meta name="twitter:description" content="${escapeHtml(seo.twitter.description)}">`,
    seo.twitter.image ? `<meta name="twitter:image" content="${escapeHtml(seo.twitter.image)}">` : "",
    `<script type="application/ld+json">${JSON.stringify(seo.jsonLd).replace(/</g, "\\u003c")}</script>`
  ].filter(Boolean).join("\n");
}
