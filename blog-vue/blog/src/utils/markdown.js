import hljs from "highlight.js/lib/core";
import "highlight.js/styles/atom-one-dark.css";
import bash from "highlight.js/lib/languages/bash";
import c from "highlight.js/lib/languages/c";
import cpp from "highlight.js/lib/languages/cpp";
import css from "highlight.js/lib/languages/css";
import dockerfile from "highlight.js/lib/languages/dockerfile";
import java from "highlight.js/lib/languages/java";
import javascript from "highlight.js/lib/languages/javascript";
import json from "highlight.js/lib/languages/json";
import markdown from "highlight.js/lib/languages/markdown";
import php from "highlight.js/lib/languages/php";
import python from "highlight.js/lib/languages/python";
import sql from "highlight.js/lib/languages/sql";
import typescript from "highlight.js/lib/languages/typescript";
import xml from "highlight.js/lib/languages/xml";
import yaml from "highlight.js/lib/languages/yaml";

[
  ["bash", bash], ["sh", bash], ["shell", bash], ["c", c], ["cpp", cpp],
  ["csharp", c], ["css", css], ["dockerfile", dockerfile], ["java", java],
  ["javascript", javascript], ["js", javascript], ["json", json], ["markdown", markdown],
  ["md", markdown], ["php", php], ["python", python], ["py", python], ["sql", sql],
  ["typescript", typescript], ["ts", typescript], ["xml", xml], ["html", xml],
  ["vue", xml], ["yaml", yaml], ["yml", yaml]
].forEach(([name, language]) => hljs.registerLanguage(name, language));

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  }[character]));
}

function createCodeIndex() {
  let seed = Date.now();
  if (globalThis.performance && typeof globalThis.performance.now === "function") {
    seed += globalThis.performance.now();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, character => {
    const random = (seed + Math.random() * 16) % 16 | 0;
    seed = Math.floor(seed / 16);
    return (character === "x" ? random : (random & 0x3) | 0x8).toString(16);
  });
}

/**
 * Shared code-block renderer for public Markdown content.
 *
 * Keep the copy affordance and line numbers in one place while using the
 * current Highlight.js API. Returning an empty string lets markdown-it use
 * its escaped fallback for unknown languages.
 */
export function renderMarkdownCode(source, language) {
  if (!language || !hljs.getLanguage(language)) return "";

  const codeIndex = createCodeIndex();
  const highlighted = hljs.highlight(source, {
    language,
    ignoreIllegals: true
  }).value;
  const linesLength = source.split(/\n/).length - 1;
  let linesNum = '<span aria-hidden="true" class="line-numbers-rows">';
  for (let index = 0; index < linesLength; index++) {
    linesNum += "<span></span>";
  }
  linesNum += "</span>";

  let html = `<button class="copy-btn iconfont iconfuzhi" type="button" aria-label="复制代码" data-clipboard-action="copy" data-clipboard-target="#copy${codeIndex}"><span class="copy-glyph" aria-hidden="true"></span><span class="copy-label">复制</span></button>${highlighted}`;
  if (linesLength) html += `<b class="name">${escapeHtml(language)}</b>`;

  return `<pre class="hljs"><code>${html}</code>${linesNum}</pre><textarea style="position: absolute;top: -9999px;left: -9999px;z-index: -9999;" id="copy${codeIndex}">${escapeHtml(
    source
  )}</textarea>`;
}

export { hljs };
