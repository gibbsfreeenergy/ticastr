<template>
  <section class="article-content-state" aria-live="polite" :aria-busy="loading">
    <div v-if="loading" class="article-state">正在加载 Markdown 内容…</div>
    <div v-else-if="error" class="article-state article-state-error" role="alert">
      <p>{{ error.kind === "not-found" ? "文章正文不存在" : "文章正文暂时无法读取" }}</p>
      <v-btn color="primary" size="small" @click="$emit('retry')">重试正文</v-btn>
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
  max-width: 760px;
  margin: 3rem auto;
  padding: 2rem;
  text-align: center;
}

.article-state-error {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
</style>
