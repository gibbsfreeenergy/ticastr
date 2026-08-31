import { describe, expect, it } from "vitest";
import { renderMarkdown } from "./utils/renderMarkdown";

describe("shared Markdown renderer", () => {
  it("does not pass through executable HTML or javascript links", () => {
    const html = renderMarkdown('<script>alert(1)</script> [bad](javascript:alert(1)) **safe**');

    expect(html).not.toContain("<script");
    expect(html.toLowerCase()).not.toMatch(/href=["']javascript:/);
    expect(html).toContain("<strong>safe</strong>");
  });
});
