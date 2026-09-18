import { describe, expect, it } from "vitest";
import { resolveArticleId } from "./articleRoute";

describe("article route", () => {
  it("does not treat the new article route as an existing article", () => {
    expect(resolveArticleId({ params: { articleId: "new" }, path: "/articles/new" })).toBeNull();
  });

  it("returns the article id for an existing article route", () => {
    expect(resolveArticleId({ params: { articleId: "42" }, path: "/articles/42" })).toBe("42");
  });
});
