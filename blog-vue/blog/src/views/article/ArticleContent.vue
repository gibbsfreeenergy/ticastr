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
