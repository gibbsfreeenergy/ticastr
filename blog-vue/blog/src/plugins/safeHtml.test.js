import { describe, expect, it } from "vitest";
import { sanitizeHtml } from "./safeHtml";

describe("sanitizeHtml", () => {
  it("removes executable markup while retaining formatting", () => {
    const html = sanitizeHtml('<img src="x" onerror="alert(1)"><mark>match</mark><script>alert(2)</script>');

    expect(html).toContain("<mark>match</mark>");
    expect(html).not.toContain("onerror");
    expect(html).not.toContain("script");
  });
});
