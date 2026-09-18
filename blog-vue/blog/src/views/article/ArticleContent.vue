<template>
  <section class="article-content-state" aria-live="polite" :aria-busy="loading">
    <div v-if="loading" class="article-state article-content-loading">正在把文字铺开…</div>
    <div v-else-if="error" class="article-state article-state-error" role="alert">
      <p>{{ error.kind === "not-found" ? "文章正文不存在" : "文章正文暂时无法读取" }}</p>
      <v-btn color="primary" variant="tonal" size="small" @click="$emit('retry')">重试正文</v-btn>
    </div>
    <article
      v-else
      id="write"
      ref="article"
      class="article-content markdown-body"
      v-safe-html="renderedContent"
    />
  </section>
</template>

<script>
export default {
  name: "ArticleContent",
  emits: ["retry"],
  props: {
    renderedContent: { type: String, default: "" },
    loading: { type: Boolean, default: false },
    error: { type: Object, default: null }
  }
};
</script>

<style scoped>
.article-state {
  padding: 50px 20px;
  color: var(--muted);
  text-align: center;
}

.article-content-loading {
  min-height: 260px;
  padding-top: 100px;
}

.article-state-error {
  margin: 0 0 30px;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--paper-strong);
}

.article-state-error p {
  margin: 0 0 18px !important;
}

.article-content {
  min-width: 0;
  color: var(--ink-soft);
  font-size: 17px;
  line-height: 2.08;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4) {
  border-bottom: 0;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-weight: 500;
  letter-spacing: -0.035em;
  line-height: 1.35;
}

.article-content :deep(h1) {
  margin: 0 0 28px;
  font-size: 38px;
}

.article-content :deep(h2) {
  position: relative;
  margin: 55px 0 18px;
  padding: 0 0 15px 20px;
  border-bottom: 1px solid var(--line);
  font-size: 29px;
}

.article-content :deep(h2)::before {
  position: absolute;
  top: 0.2em;
  bottom: 0.2em;
  left: 0;
  width: 3px;
  border-radius: 3px;
  background: var(--sage);
  content: "";
}

.article-content :deep(h3) {
  margin: 36px 0 12px;
  font-size: 23px;
}

.article-content :deep(p) {
  margin: 0 0 22px !important;
  color: var(--ink-soft);
  font-size: 17px;
}

.article-content :deep(a) {
  color: var(--sage-deep) !important;
  text-decoration: underline;
  text-decoration-color: rgba(85, 118, 107, 0.35);
  text-underline-offset: 4px;
}

.article-content :deep(blockquote) {
  margin: 32px 0;
  padding: 20px 24px;
  border-left: 2px solid var(--sage);
  color: var(--muted);
  background: rgba(143, 169, 154, 0.1);
}

.article-content :deep(img) {
  display: block;
  max-width: 100%;
  margin: 34px auto;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  cursor: zoom-in;
}

.article-content :deep(figure) {
  margin: 34px 0;
}

.article-content :deep(figcaption) {
  margin-top: -20px;
  color: var(--muted-light);
  font-size: 12px;
  text-align: center;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 0 0 24px;
  padding-left: 1.5em;
}

.article-content :deep(li) {
  padding-left: 5px;
}

.article-content :deep(hr) {
  margin: 44px 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
}

.article-content :deep(pre.hljs) {
  margin: 30px 0;
  padding: 52px 24px 22px !important;
  overflow: auto !important;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-md) !important;
  background: #202a35 !important;
  font-size: 13px !important;
  line-height: 1.8 !important;
}

.article-content :deep(code:not(.hljs code)) {
  padding: 2px 6px;
  border-radius: 5px;
  color: var(--sage-deep);
  background: rgba(143, 169, 154, 0.13);
  font-size: 0.86em;
}

@media (max-width: 760px) {
  .article-content {
    font-size: 16px;
    line-height: 1.95;
  }

  .article-content :deep(h1) {
    font-size: 30px;
  }

  .article-content :deep(h2) {
    margin-top: 42px;
    font-size: 25px;
  }

  .article-content :deep(p) {
    font-size: 16px;
  }
}
</style>
