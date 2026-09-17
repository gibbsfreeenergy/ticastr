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

<style scoped>
.right-container {
  padding: 20px 24px;
  font-size: 14px;
}

.right-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 16.8px;
  line-height: 2;
}

.right-title i {
  font-weight: bold;
}

.article-item {
  display: flex;
  align-items: center;
  padding: 6px 0;
}

.article-item:first-child {
  padding-top: 0;
}

.article-item:last-child {
  padding-bottom: 0;
}

.article-item:not(:last-child) {
  border-bottom: 1px dashed #f5f5f5;
}

.article-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s;
}

.article-item img:hover {
  transform: scale(1.1);
}

.content {
  display: -webkit-box;
  flex: 1;
  padding-left: 10px;
  overflow: hidden;
  word-break: break-all;
  -webkit-box-orient: vertical;
}

.content-cover {
  display: block;
  width: 58.8px;
  height: 58.8px;
  overflow: hidden;
}

.content-title a {
  font-size: 95%;
  transition: color 0.2s;
}

.content-title a:hover {
  color: #2ba1d1;
}

.content-time {
  color: #858585;
  font-size: 85%;
  line-height: 2;
}
</style>
