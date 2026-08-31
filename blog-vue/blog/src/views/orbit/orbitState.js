const DEFAULT_CATEGORY = "未分类";

function text(value, fallback = "") {
  const result = String(value ?? "").trim();
  return result || fallback;
}

export function normalizeArticles(items) {
  return (Array.isArray(items) ? items : []).filter(Boolean).map((item, sourceIndex) => {
    const tags = Array.isArray(item.tagDTOList)
      ? item.tagDTOList.map(tag => text(tag?.tagName)).filter(Boolean)
      : [];
    const wordCount = Number(item.wordCount ?? item.wordNum ?? 0);
    const readingMinutes = Number.isFinite(wordCount) && wordCount > 0
      ? Math.max(1, Math.round(wordCount / 400))
      : 1;
    const title = text(item.articleTitle, "未命名文章");
    const summary = text(item.articleSummary ?? item.summary ?? item.articleIntro);
    const categoryName = text(item.categoryName, DEFAULT_CATEGORY);
    return {
      id: item.id ?? item.articleId ?? `article-${sourceIndex}`,
      title,
      summary,
      categoryId: item.categoryId ?? null,
      categoryName,
      tags,
      createTime: item.createTime ?? null,
      readingMinutes,
      sourceIndex,
      searchText: [title, summary, categoryName, ...tags].join(" ").toLocaleLowerCase()
    };
  });
}

export function getOrbitCategories(articles) {
  return [...new Set((articles || []).map(article => article.categoryName).filter(Boolean))];
}

export function filterOrbitArticles(articles, { query = "", category = "全部" } = {}) {
  const needle = String(query).trim().toLocaleLowerCase();
  return (articles || []).filter(article => {
    const matchesCategory = category === "全部" || article.categoryName === category;
    return matchesCategory && (!needle || article.searchText.includes(needle));
  });
}

export function sortOrbitArticles(articles, mode = "latest") {
  return [...(articles || [])].sort((left, right) => {
    if (mode === "reading" && right.readingMinutes !== left.readingMinutes) {
      return right.readingMinutes - left.readingMinutes;
    }
    const leftTime = Date.parse(left.createTime || "") || 0;
    const rightTime = Date.parse(right.createTime || "") || 0;
    if (mode === "oldest" && leftTime !== rightTime) return leftTime - rightTime;
    if (mode !== "oldest" && leftTime !== rightTime) return rightTime - leftTime;
    return left.sourceIndex - right.sourceIndex;
  });
}

export function getOrbitPosition(index, count) {
  const safeCount = Math.max(1, count);
  const angle = (index * 137.5 - 90) * (Math.PI / 180);
  const radius = Math.min(37, 18 + safeCount * 1.3);
  const x = Math.min(92, Math.max(8, 50 + Math.cos(angle) * radius));
  const y = Math.min(88, Math.max(12, 50 + Math.sin(angle) * radius * 0.72));
  return { left: `${x}%`, top: `${y}%`, x, y };
}

export function chooseSurprise(articles, currentId) {
  const list = articles || [];
  if (list.length < 2) return list[0] || null;
  return list.find(article => article.id !== currentId) || list[0];
}
