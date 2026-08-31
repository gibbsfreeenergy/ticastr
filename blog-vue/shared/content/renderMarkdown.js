const DEFAULT_MARKDOWN_OPTIONS = Object.freeze({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
});

/**
 * Shared Markdown policy. Runtime dependencies are injected by each app so
 * the shared package does not resolve one app's node_modules from outside its
 * package boundary.
 */
export function createMarkdownRenderer({
  MarkdownIt,
  sanitize,
  markdown = {},
  highlight
} = {}) {
  if (typeof MarkdownIt !== "function" || typeof sanitize !== "function") {
    throw new TypeError("createMarkdownRenderer requires MarkdownIt and sanitize");
  }
  const markdownOptions = {
    ...DEFAULT_MARKDOWN_OPTIONS,
    ...markdown
  };
  if (typeof highlight === "function") markdownOptions.highlight = highlight;
  const parser = new MarkdownIt(markdownOptions);
  return source => sanitize(parser.render(String(source ?? "")));
}
