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
      <span class="reward-btn" v-if="blogInfo.websiteConfig.isReward == 1" tabindex="0">
        <i class="iconfont iconerweima" /> 打赏
        <span class="animated fadeInDown reward-main">
          <span class="reward-all">
            <span class="reward-item"><img class="reward-img" :src="blogInfo.websiteConfig.weiXinQRCode" alt="微信收款码" width="130" height="130" loading="lazy" /><span class="reward-desc">微信</span></span>
            <span class="reward-item"><img class="reward-img" :src="blogInfo.websiteConfig.alipayQRCode" alt="支付宝收款码" width="130" height="130" loading="lazy" /><span class="reward-desc">支付宝</span></span>
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
