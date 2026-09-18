<template>
  <header class="article-hero" :style="coverStyle">
    <div class="article-hero-fade" aria-hidden="true" />
    <div class="article-hero-inner page-width">
      <div class="article-hero-copy fade-up">
        <span class="article-hero-label">随笔 · 文章</span>
        <h1>{{ article.articleTitle }}</h1>
        <div class="article-info">
          <span>{{ date(article.createTime) }}</span>
          <span class="info-dot" aria-hidden="true" />
          <span>阅读时长 {{ readTime || "1分钟" }}</span>
          <span class="info-dot" aria-hidden="true" />
          <span>{{ num(wordNum) }} 字</span>
        </div>
      </div>
      <div class="article-hero-side" aria-hidden="true">向生活靠近<br />也向自己靠近</div>
    </div>
  </header>
</template>

<script>
export default {
  name: "ArticleMeta",
  props: {
    article: { type: Object, required: true },
    wordNum: { type: [Number, String], default: 0 },
    readTime: { type: String, default: "" },
    coverStyle: { type: [Object, String], default: "" }
  }
};
</script>

<style scoped>
.article-hero {
  position: relative;
  min-height: 480px;
  overflow: hidden;
  background-color: #80919e;
  background-position: center;
  background-size: cover;
  color: #fff;
}

.article-hero-fade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 32, 43, 0.54), rgba(18, 32, 43, 0.08) 75%), linear-gradient(180deg, rgba(18, 32, 43, 0.16), rgba(18, 32, 43, 0.1) 58%, var(--paper) 100%);
}

.article-hero-inner {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 480px;
  padding-top: 120px;
  padding-bottom: 92px;
}

.article-hero-copy {
  max-width: 770px;
}

.article-hero-label {
  display: block;
  margin-bottom: 18px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 11px;
  letter-spacing: 0.2em;
}

.article-hero h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(36px, 5vw, 64px);
  font-weight: 400;
  letter-spacing: -0.05em;
  line-height: 1.2;
  text-shadow: 0 4px 22px rgba(18, 32, 43, 0.2);
}

.article-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 11px;
  margin-top: 28px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 13px;
}

.info-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--blush);
}

.article-hero-side {
  padding: 0 0 8px 22px;
  border-left: 1px solid rgba(255, 255, 255, 0.56);
  color: rgba(255, 255, 255, 0.82);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 15px;
  letter-spacing: 0.12em;
  line-height: 1.9;
}

@media (max-width: 760px) {
  .article-hero,
  .article-hero-inner {
    min-height: 430px;
  }

  .article-hero-inner {
    padding-top: 100px;
    padding-bottom: 68px;
  }

  .article-hero h1 {
    font-size: 34px;
  }

  .article-hero-side {
    display: none;
  }
}
</style>
