<template>
  <aside class="article-sidebar">
    <v-card class="right-container">
      <div class="right-title"><i class="iconfont iconhanbao" style="font-size:16.8px" /><span>目录</span></div>
      <div id="toc" aria-label="文章目录" />
    </v-card>
    <v-card class="right-container" style="margin-top:20px">
      <div class="right-title"><i class="iconfont icongengxinshijian" style="font-size:16.8px" /><span>最新文章</span></div>
      <div class="article-list">
        <div class="article-item" v-for="item of article.newestArticleList" :key="item.id">
          <router-link :to="'/articles/' + item.id" class="content-cover">
            <img :src="item.articleCover" :alt="item.articleTitle" width="59" height="59" loading="lazy" decoding="async" />
          </router-link>
          <div class="content">
            <div class="content-title"><router-link :to="'/articles/' + item.id">{{ item.articleTitle }}</router-link></div>
            <div class="content-time">{{ date(item.createTime) }}</div>
          </div>
        </div>
      </div>
    </v-card>
  </aside>
</template>

<script>
export default {
  name: "ArticleSidebar",
  props: { article: { type: Object, required: true } }
};
</script>
