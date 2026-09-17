<template>
  <section>
    <div class="aritcle-copyright">
      <div><span>文章作者：</span><router-link to="/">{{ blogInfo.websiteConfig.websiteAuthor }}</router-link></div>
      <div><span>文章链接：</span><a :href="articleHref" target="_blank" rel="noopener">{{ articleHref }}</a></div>
      <div><span>版权声明：</span>本博客所有文章除特别声明外，均采用
        <a href="https://creativecommons.org/licenses/by-nc-sa/4.0/" target="_blank" rel="noopener">CC BY-NC-SA 4.0</a>许可协议。转载请注明文章出处。
      </div>
    </div>
    <div class="article-operation">
      <div class="tag-container">
        <router-link v-for="item of article.tagDTOList" :key="item.id" :to="'/tags/' + item.id">{{ item.tagName }}</router-link>
      </div>
      <v-btn style="margin-left:auto" variant="text" icon="$mdi-share-variant" aria-label="分享文章" @click="$emit('share')" />
    </div>
    <div class="article-reward">
      <button type="button" :class="isLike" @click="$emit('like')">
        <v-icon size="14" color="#fff">$mdi-thumb-up</v-icon> 点赞
        <span v-show="article.likeCount > 0">{{ article.likeCount }}</span>
      </button>
      <span
        class="reward-btn"
        v-if="blogInfo.websiteConfig.isReward == 1 && (blogInfo.websiteConfig.weiXinQRCode || blogInfo.websiteConfig.alipayQRCode)"
        tabindex="0"
      >
        <i class="iconfont iconerweima" /> 打赏
        <span class="animated fadeInDown reward-main">
          <span class="reward-all">
            <span class="reward-item" v-if="blogInfo.websiteConfig.weiXinQRCode"><img class="reward-img" :src="blogInfo.websiteConfig.weiXinQRCode" alt="微信收款码" width="130" height="130" loading="lazy" /><span class="reward-desc">微信</span></span>
            <span class="reward-item" v-if="blogInfo.websiteConfig.alipayQRCode"><img class="reward-img" :src="blogInfo.websiteConfig.alipayQRCode" alt="支付宝收款码" width="130" height="130" loading="lazy" /><span class="reward-desc">支付宝</span></span>
          </span>
        </span>
      </span>
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
      <div class="recommend-title"><v-icon size="20" color="#4c4948">$mdi-thumb-up</v-icon> 相关推荐</div>
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
  emits: ["share", "like"],
  props: {
    article: { type: Object, required: true },
    blogInfo: { type: Object, required: true },
    articleHref: { type: String, required: true },
    isLike: { type: String, default: "like-btn" }
  },
  methods: {
    isFull(id) { return id ? "post full" : "post"; }
  }
};
</script>

<style scoped>
.article-operation {
  display: flex;
  align-items: center;
}

.tag-container a {
  display: inline-block;
  width: fit-content;
  margin: 0.5rem 0.5rem 0.5rem 0;
  padding: 0 0.75rem;
  border: 1px solid #49b1f5;
  border-radius: 1rem;
  color: #49b1f5 !important;
  font-size: 12px;
  line-height: 2;
}

.tag-container a:hover {
  background: #49b1f5;
  color: #fff !important;
  transition: all 0.5s;
}

.aritcle-copyright {
  position: relative;
  margin: 40px 0 10px;
  padding: 0.625rem 1rem;
  border: 1px solid #eee;
  font-size: 0.875rem;
  line-height: 2;
}

.aritcle-copyright span {
  color: #49b1f5;
  font-weight: bold;
}

.aritcle-copyright a {
  color: #99a9bf !important;
  text-decoration: underline !important;
}

.aritcle-copyright::before {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  width: 1rem;
  height: 1rem;
  border-radius: 1rem;
  background: #49b1f5;
  content: "";
}

.aritcle-copyright::after {
  position: absolute;
  top: 0.95rem;
  right: 0.95rem;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 0.5em;
  background: #fff;
  content: "";
}

.article-reward {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 5rem;
}

.like-btn,
.like-btn-active {
  display: inline-block;
  width: 100px;
  border: 0;
  color: #fff !important;
  text-align: center;
  line-height: 36px;
  font-size: 0.875rem;
  cursor: pointer;
}

.like-btn {
  background: #969696;
}

.like-btn-active {
  background: #ec7259;
}

.reward-btn {
  position: relative;
  display: inline-block;
  width: 100px;
  margin: 0 1rem;
  background: #49b1f5;
  color: #fff !important;
  text-align: center;
  line-height: 36px;
  font-size: 0.875rem;
}

.reward-main {
  position: absolute;
  bottom: 40px;
  left: 0;
  display: none;
  width: 100%;
  margin: 0;
  padding: 0 0 15px;
}

.reward-btn:hover .reward-main,
.reward-btn:focus .reward-main {
  display: block;
}

.reward-all {
  display: inline-flex;
  width: 320px;
  margin-left: -110px;
  padding: 20px 10px 8px;
  border-radius: 4px;
  background: #f5f5f5;
}

.reward-item {
  display: inline-flex;
  flex-direction: column;
  padding: 0 8px;
  list-style: none;
}

.reward-img {
  display: block;
  width: 130px;
  height: 130px;
}

.reward-desc {
  margin: -5px 0;
  color: #858585;
  text-align: center;
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
  line-height: 2;
  font-size: 14px;
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
