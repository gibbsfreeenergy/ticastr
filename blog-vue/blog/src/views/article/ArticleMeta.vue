<template>
  <header class="banner" :style="coverStyle">
    <div class="article-info-container">
      <h1 class="article-title">{{ article.articleTitle }}</h1>
      <div class="article-info">
        <div class="first-line">
          <span><i class="iconfont iconrili" /> 发表于 {{ date(article.createTime) }}</span>
          <span class="separator">|</span>
          <span><i class="iconfont icongengxinshijian" /> 更新于 {{ date(article.updateTime || article.createTime) }}</span>
          <span class="separator">|</span>
          <span class="article-category">
            <i class="iconfont iconfenlei1" />
            <router-link :to="'/categories/' + article.categoryId">{{ article.categoryName || "未分类" }}</router-link>
          </span>
        </div>
        <div class="second-line">
          <span><i class="iconfont iconzishu" /> 字数统计: {{ num(wordNum) }}</span>
          <span class="separator">|</span>
          <span><i class="iconfont iconshijian" /> 阅读时长: {{ readTime }}</span>
        </div>
        <div class="third-line">
          <span class="separator">|</span>
          <span><i class="iconfont iconliulan" /> 阅读量: {{ article.viewsCount || 0 }}</span>
          <span class="separator">|</span>
          <span><i class="iconfont iconpinglunzu1" /> 评论数: {{ commentCount }}</span>
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
    commentCount: { type: Number, default: 0 },
    coverStyle: { type: String, default: "" }
  }
};
</script>
