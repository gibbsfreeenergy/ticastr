import { describe, expect, it } from "vitest";
import { articleSeo, applySeo, clearSeoHead, staticSeoTags } from "./seo";

describe("SEO contract", () => {
  it("creates an article canonical URL and escaped metadata", () => {
    const seo = articleSeo({ id: 12, articleTitle: "A & B", articleIntro: "摘要" }, {
      origin: "https://blog.example.com",
      siteName: "Ticastr"
    });

    expect(seo.canonical).toBe("https://blog.example.com/articles/12/");
    expect(seo.og.type).toBe("article");
    expect(staticSeoTags(seo)).toContain("A &amp; B");
  });

  it("replaces only managed head nodes and emits JSON-LD", () => {
    document.title = "old";
    const first = applySeo({ id: 1, articleTitle: "First" }, { origin: "https://blog.example.com" });
    const second = applySeo({ id: 2, articleTitle: "Second" }, { origin: "https://blog.example.com" });

    expect(first.canonical).toContain("/articles/1/");
    expect(second.canonical).toContain("/articles/2/");
    expect(document.title).toBe("Second");
    expect(document.head.querySelectorAll("[data-ticastr-seo]")).toHaveLength(10);
    expect(document.head.querySelector('script[type="application/ld+json"]')?.textContent).toContain("Second");
    clearSeoHead();
    expect(document.head.querySelectorAll("[data-ticastr-seo]")).toHaveLength(0);
  });
});
