<template>
  <section>
    <div class="article-copyright">
      <div><span>文章作者：</span><router-link to="/">{{ blogInfo.websiteConfig.websiteAuthor }}</router-link></div>
      <div><span>文章链接：</span><a :href="articleHref" target="_blank" rel="noopener">{{ articleHref }}</a></div>
      <div><span>版权声明：</span>本博客所有文章除特别声明外，均采用
        <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank" rel="noopener">CC BY-NC-SA 4.0</a>许可协议。转载请注明文章出处。
      </div>
    </div>
    <div class="pagination-post">
      <div v-if="article.lastArticle.id" :class="isFull(article.lastArticle.id)">
        <router-link :to="'/articles/' + article.lastArticle.id">
          <img class="post-cover" :src="article.lastArticle.articleCover" :alt="article.lastArticle.articleTitle" width="360" height="150" loading="lazy" decoding="async" />
          <div class="post-info"><div class="label">上一篇</div><div class="post-title">{{ article.lastArticle.articleTitle }}</div></div>
        </router-link>
      </div>
      <div v-if="article.nextArticle.id" :class="isFull(article.nextArticle.id)">
        <router-link :to="'/articles/' + article.nextArticle.id">
          <img class="post-cover" :src="article.nextArticle.articleCover" :alt="article.nextArticle.articleTitle" width="360" height="150" loading="lazy" decoding="async" />
          <div class="post-info" style="text-align:right"><div class="label">下一篇</div><div class="post-title">{{ article.nextArticle.articleTitle }}</div></div>
        </router-link>
      </div>
    </div>
    <div v-if="article.recommendArticleList.length" class="recommend-container">
      <div class="recommend-title"><v-icon size="20" color="#4c4948">$mdi-book-open-variant</v-icon> 相关推荐</div>
      <div class="recommend-list">
        <div class="recommend-item" v-for="item of article.recommendArticleList" :key="item.id">
          <router-link :to="'/articles/' + item.id">
            <img class="recommend-cover" :src="item.articleCover" :alt="item.articleTitle" width="320" height="200" loading="lazy" decoding="async" />
            <div class="recommend-info"><div class="recommend-date"><i class="iconfont iconrili" /> {{ date(item.createTime) }}</div><div>{{ item.articleTitle }}</div></div>
          </router-link>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: "ArticleNavigation",
  props: {
    article: { type: Object, required: true },
    blogInfo: { type: Object, required: true },
    articleHref: { type: String, required: true }
  },
  methods: {
    isFull(id) { return id ? "post full" : "post"; }
  }
};
</script>

<style scoped>
.article-copyright {
  position: relative;
  margin: 40px 0 10px;
  padding: 0.625rem 1rem;
  border: 1px solid #eee;
  font-size: 0.875rem;
  line-height: 2;
}

.article-copyright span {
  color: #49b1f5;
  font-weight: bold;
}

.article-copyright a {
  color: #99a9bf !important;
  text-decoration: underline !important;
}

.article-copyright::before {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  width: 1rem;
  height: 1rem;
  border-radius: 1rem;
  background: #49b1f5;
  content: "";
}

.article-copyright::after {
  position: absolute;
  top: 0.95rem;
  right: 0.95rem;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 0.5em;
  background: #fff;
  content: "";
}

.pagination-post {
  display: flex;
  width: 100%;
  margin-top: 40px;
  overflow: hidden;
  background: #000;
}

.post {
  position: relative;
  width: 50%;
  height: 150px;
  overflow: hidden;
}

.post.full {
  width: 100%;
}

.post a {
  position: relative;
  display: block;
  height: 150px;
  overflow: hidden;
}

.post-info {
  position: absolute;
  top: 50%;
  width: 100%;
  padding: 20px 40px;
  transform: translateY(-50%);
  font-size: 14px;
  line-height: 2;
}

.post-cover,
.recommend-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.4;
  transition: transform 0.6s;
}

.post-cover {
  position: absolute;
}

.post:hover .post-cover,
.recommend-item:hover .recommend-cover {
  opacity: 0.8;
  transform: scale(1.1);
}

.label {
  color: #eee;
  font-size: 90%;
}

.post-title {
  color: #fff;
  font-weight: 500;
}

.recommend-container {
  margin-top: 40px;
}

.recommend-title {
  margin-bottom: 5px;
  font-size: 20px;
  font-weight: bold;
  line-height: 2;
}

.recommend-item {
  position: relative;
  display: inline-block;
  width: calc(33.333% - 6px);
  height: 200px;
  margin: 3px;
  overflow: hidden;
  background: #000;
  vertical-align: bottom;
}

.recommend-item a {
  display: block;
  height: 100%;
}

.recommend-info {
  position: absolute;
  top: 50%;
  width: 100%;
  padding: 0 20px;
  transform: translateY(-50%);
  color: #fff;
  text-align: center;
  line-height: 2;
  font-size: 14px;
}

.recommend-date {
  font-size: 90%;
}

@media (max-width: 759px) {
  .pagination-post {
    display: block;
  }

  .post,
  .post.full {
    width: 100%;
  }

  .recommend-item {
    width: calc(100% - 4px);
    height: 150px;
    margin: 2px;
  }
}
</style>
