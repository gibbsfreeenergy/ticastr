<template>
  <section class="article-preview" aria-label="Markdown 预览">
    <div v-if="!markdown.trim()" class="preview-empty">暂无可预览内容</div>
    <article v-else class="markdown-body" v-safe-html="renderedMarkdown" />
  </section>
</template>

<script>
import { createMarkdownRenderer } from "../../utils/renderMarkdown";

const renderMarkdown = createMarkdownRenderer();

export default {
  name: "ArticlePreview",
  props: {
    markdown: { type: String, default: "" }
  },
  computed: {
    renderedMarkdown() {
      return renderMarkdown(this.markdown);
    }
  }
};
</script>

<style scoped>
.article-preview {
  min-height: 240px;
  padding: 1.5rem;
  overflow: auto;
  background: #fff;
}

.preview-empty {
  color: #8a94a6;
  text-align: center;
  padding: 4rem 1rem;
}
</style>
