import DOMPurify from "dompurify";
import MarkdownIt from "markdown-it";
import { createMarkdownRenderer as createSharedMarkdownRenderer } from "../../../shared/content/renderMarkdown";

const sanitize = html => DOMPurify.sanitize(html, {
  USE_PROFILES: { html: true }
});

export function createMarkdownRenderer(options = {}) {
  return createSharedMarkdownRenderer({ ...options, MarkdownIt, sanitize });
}

export function renderMarkdown(source, options) {
  return createMarkdownRenderer(options)(source);
}
