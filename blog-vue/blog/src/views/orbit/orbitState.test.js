import { describe, expect, it } from "vitest";
import {
  chooseSurprise,
  filterOrbitArticles,
  getOrbitCategories,
  getOrbitPosition,
  normalizeArticles,
  sortOrbitArticles
} from "./orbitState";

const articles = normalizeArticles([
  {
    id: 1,
    articleTitle: "Redis 的第二条路",
    categoryName: "系统",
    createTime: "2026-08-30T00:00:00Z",
    tagDTOList: [{ id: 1, tagName: "性能" }],
    articleSummary: "从缓存故障里找出路",
    wordCount: 1600
  },
  {
    id: 2,
    articleTitle: "给夏天写一封信",
    categoryName: "生活",
    createTime: "2026-07-01T00:00:00Z",
    tagDTOList: [{ id: 2, tagName: "随笔" }],
    wordCount: 400
  }
]);

describe("orbitState", () => {
  it("normalizes incomplete metadata without throwing", () => {
    const [article] = normalizeArticles([null, { id: 9, articleTitle: "  标题  " }]);
    expect(article).toMatchObject({
      id: 9,
      title: "标题",
      categoryName: "未分类",
      tags: [],
      readingMinutes: 1
    });
  });

  it("derives unique categories and filters through searchable fields", () => {
    expect(getOrbitCategories(articles)).toEqual(["系统", "生活"]);
    expect(filterOrbitArticles(articles, { query: "性能" })).toHaveLength(1);
    expect(filterOrbitArticles(articles, { category: "生活" })[0].id).toBe(2);
  });

  it("sorts by date and reading duration while preserving stable ties", () => {
    expect(sortOrbitArticles(articles, "latest").map(item => item.id)).toEqual([1, 2]);
    expect(sortOrbitArticles(articles, "oldest").map(item => item.id)).toEqual([2, 1]);
    expect(sortOrbitArticles(articles, "reading").map(item => item.id)).toEqual([1, 2]);
  });

  it("returns bounded deterministic positions and a different surprise when possible", () => {
    expect(getOrbitPosition(3, 8)).toEqual(getOrbitPosition(3, 8));
    const position = getOrbitPosition(3, 8);
    expect(position.x).toBeGreaterThanOrEqual(8);
    expect(position.x).toBeLessThanOrEqual(92);
    expect(position.y).toBeGreaterThanOrEqual(12);
    expect(position.y).toBeLessThanOrEqual(88);
    expect(chooseSurprise(articles, 1).id).toBe(2);
    expect(chooseSurprise([articles[0]], 1).id).toBe(1);
  });
});
