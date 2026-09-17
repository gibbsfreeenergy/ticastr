<template>
  <header class="banner" :style="coverStyle">
    <div class="article-info-container">
      <h1 class="article-title">{{ article.articleTitle }}</h1>
      <div class="article-info">
        <span><i class="iconfont iconrili" /> 发表于 {{ date(article.createTime) }}</span>
        <span class="separator">|</span>
        <span><i class="iconfont icongengxinshijian" /> 更新于 {{ date(article.updateTime || article.createTime) }}</span>
        <div class="article-reading-info">
          <span><i class="iconfont iconzishu" /> 字数统计: {{ num(wordNum) }}</span>
          <span class="separator">|</span>
          <span><i class="iconfont iconshijian" /> 阅读时长: {{ readTime }}</span>
        </div>
      </div>
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
    coverStyle: { type: String, default: "" }
  }
};
</script>

<style scoped>
.banner {
  color: #eee !important;
}

.banner::before {
  position: absolute;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.5);
  content: "";
}

.article-info-container {
  position: absolute;
  bottom: 6.25rem;
  z-index: 1;
  width: 100%;
  padding: 0 8%;
  text-align: center;
}

.article-title {
  margin: 20px 0 8px;
  font-size: 35px;
}

.article-info {
  display: inline-block;
  font-size: 14px;
  line-height: 1.9;
}

.article-info span {
  font-size: 95%;
}

.article-info i {
  font-size: 14px;
}

.article-reading-info {
  display: inline;
}

@media (max-width: 759px) {
  .banner {
    height: 360px;
  }

  .article-info-container {
    bottom: 1.3rem;
    padding: 0 5%;
    text-align: left;
  }

  .article-title {
    margin-bottom: 0.4rem;
    font-size: 1.5rem;
  }

  .article-reading-info {
    display: block;
  }
}
</style>
