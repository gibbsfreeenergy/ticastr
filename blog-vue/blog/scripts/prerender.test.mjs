import test from "node:test";
import assert from "node:assert/strict";
import { renderArticleShell } from "./prerender.mjs";

test("prerendered article shell contains readable content without executable HTML", () => {
  const html = renderArticleShell(
    { id: 3, articleTitle: "文章", createTime: "2026-01-01T00:00:00Z" },
    "# 标题\n\n<script>alert(1)</script>\n\n[安全链接](https://example.com)"
  );

  assert.match(html, /<h1>文章<\/h1>/);
  assert.match(html, /<h1[^>]*>标题<\/h1>/);
  assert.doesNotMatch(html, /<script>alert/);
  assert.match(html, /href="https:\/\/example\.com"/);
});
