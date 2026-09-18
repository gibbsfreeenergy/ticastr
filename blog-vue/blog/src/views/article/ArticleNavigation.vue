<template>
  <section class="article-navigation">
    <div class="article-copyright">
      <div><span>作者</span><router-link to="/">{{ blogInfo.websiteConfig.websiteAuthor }}</router-link></div>
      <div><span>链接</span><a :href="articleHref" target="_blank" rel="noopener">{{ articleHref }}</a></div>
      <div><span>版权</span>除特别声明外，文章采用 <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank" rel="noopener">CC BY-NC-SA 4.0</a> 许可协议。</div>
    </div>

    <div class="pagination-post">
      <div v-if="article.lastArticle.id" class="post">
        <router-link :to="'/articles/' + article.lastArticle.id">
          <img class="post-cover" :src="article.lastArticle.articleCover" :alt="article.lastArticle.articleTitle" loading="lazy" decoding="async" />
          <div class="post-info"><span>上一篇</span><strong>{{ article.lastArticle.articleTitle }}</strong></div>
        </router-link>
      </div>
      <div v-if="article.nextArticle.id" class="post post-next">
        <router-link :to="'/articles/' + article.nextArticle.id">
          <img class="post-cover" :src="article.nextArticle.articleCover" :alt="article.nextArticle.articleTitle" loading="lazy" decoding="async" />
          <div class="post-info"><span>下一篇</span><strong>{{ article.nextArticle.articleTitle }}</strong></div>
        </router-link>
      </div>
    </div>

    <div v-if="article.recommendArticleList.length" class="recommend-container">
      <div class="recommend-heading"><span>相关推荐</span><i aria-hidden="true">✦</i></div>
      <div class="recommend-list">
        <router-link v-for="item of article.recommendArticleList" :key="item.id" class="recommend-item" :to="'/articles/' + item.id">
          <img class="recommend-cover" :src="item.articleCover" :alt="item.articleTitle" loading="lazy" decoding="async" />
          <span>
            <small>{{ date(item.createTime) }}</small>
            <strong>{{ item.articleTitle }}</strong>
          </span>
        </router-link>
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
  }
};
</script>

<style scoped>
.article-navigation {
  margin-top: 72px;
}

.article-copyright {
  position: relative;
  padding: 19px 22px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.9;
}

.article-copyright div {
  display: flex;
  gap: 12px;
  overflow-wrap: anywhere;
}

.article-copyright span {
  flex: 0 0 28px;
  color: var(--sage-deep);
}

.article-copyright a {
  color: var(--sage-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pagination-post {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 38px;
}

.post {
  min-width: 0;
}

.post:only-child {
  grid-column: 1 / -1;
}

.post a {
  position: relative;
  display: block;
  height: 140px;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--ink);
}

.post-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.45;
  transition: opacity 300ms ease, transform 600ms ease;
}

.post a:hover .post-cover {
  opacity: 0.65;
  transform: scale(1.055);
}

.post-info {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 20px 24px;
  color: #fff;
}

.post-info span {
  margin-bottom: 7px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 11px;
  letter-spacing: 0.13em;
}

.post-info strong {
  overflow: hidden;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-next .post-info {
  align-items: flex-end;
  text-align: right;
}

.recommend-container {
  margin-top: 58px;
}

.recommend-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 19px;
}

.recommend-heading i {
  color: var(--gold);
  font-style: normal;
}

.recommend-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.recommend-item {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  transition: border-color 180ms ease, transform 180ms ease;
}

.recommend-item:hover {
  border-color: var(--sage);
  transform: translateY(-3px);
}

.recommend-cover {
  width: 82px;
  height: 70px;
  border-radius: 8px;
  object-fit: cover;
}

.recommend-item span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.recommend-item small {
  color: var(--muted-light);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 10px;
}

.recommend-item strong {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .article-navigation {
    margin-top: 54px;
  }

  .pagination-post {
    display: block;
  }

  .post + .post {
    margin-top: 12px;
  }

  .recommend-list {
    grid-template-columns: 1fr;
  }
}
</style>
