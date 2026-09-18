import { describe, expect, it } from "vitest";
import { renderMarkdownCode } from "./utils/markdown";
import { renderMarkdown } from "./utils/renderMarkdown";

describe("shared Markdown renderer", () => {
  it("does not pass through executable HTML or javascript links", () => {
    const html = renderMarkdown('<script>alert(1)</script> [bad](javascript:alert(1)) **safe**');

    expect(html).not.toContain("<script");
    expect(html.toLowerCase()).not.toMatch(/href=["']javascript:/);
    expect(html).toContain("<strong>safe</strong>");
  });

  it("uses the shared link, typography, and line-break policy", () => {
    const html = renderMarkdown("https://example.com\n第二行\n\n<em>raw html</em>");

    expect(html).toContain('<a href="https://example.com">https://example.com</a>');
    expect(html).toContain("<br>");
    expect(html).toContain("&lt;em&gt;raw html&lt;/em&gt;");
  });

  it("renders highlighted code with the current Highlight.js API", () => {
    const html = renderMarkdown("```js\nconst value = 1;\n```", {
      highlight: renderMarkdownCode
    });

    expect(html).toContain('class="copy-btn iconfont iconfuzhi"');
    expect(html).toContain('class="hljs"');
    expect(html).toContain("const");
  });
});
