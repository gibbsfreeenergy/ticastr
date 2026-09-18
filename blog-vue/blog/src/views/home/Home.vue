<template>
  <div>
    <div class="home-banner" :style="cover">
      <div class="banner-container">
        <h1 class="blog-title">{{ blogInfo.websiteConfig.websiteName }}</h1>
        <div class="blog-intro">{{ blogInfo.websiteConfig.websiteIntro }}</div>
        <div class="blog-contact">
          <a
            v-if="isShowSocial('qq')"
            class="mr-5 iconfont iconqq"
            target="_blank"
            rel="noopener"
            :href="'http://wpa.qq.com/msgrd?v=3&uin=' + blogInfo.websiteConfig.qq + '&site=qq&menu=yes'"
          />
          <a
            v-if="isShowSocial('github')"
            target="_blank"
            rel="noopener"
            :href="blogInfo.websiteConfig.github"
            class="mr-5 iconfont icongithub"
          />
          <a
            v-if="isShowSocial('gitee')"
            target="_blank"
            rel="noopener"
            :href="blogInfo.websiteConfig.gitee"
            class="iconfont icongitee-fill-round"
          />
        </div>
      </div>
      <div class="scroll-down" @click="scrollDown">
        <v-icon color="#fff" class="scroll-down-effects">$mdi-chevron-down</v-icon>
      </div>
    </div>

    <v-row class="home-container">
      <v-col md="9" cols="12">
        <v-card
          v-for="(item, index) of articleList"
          :key="item.id"
          class="article-card"
          :class="index % 2 === 0 ? 'left-card' : 'right-card'"
        >
          <div class="article-cover">
            <router-link :to="'/articles/' + item.id">
              <v-img class="on-hover" width="100%" height="100%" :src="item.articleCover" />
            </router-link>
          </div>
          <div class="article-wrapper">
            <div class="article-title-line">
              <router-link :to="'/articles/' + item.id">{{ item.articleTitle }}</router-link>
            </div>
            <div class="article-info">
              <span v-if="item.isTop == 1" class="top-mark"><i class="iconfont iconzhiding" /> 置顶</span>
              <span v-if="item.isTop == 1" class="separator">|</span>
              <v-icon size="14">$mdi-calendar-month-outline</v-icon>
              {{ date(item.createTime) }}
            </div>
            <div class="article-content article-content-hint">点击标题阅读全文</div>
          </div>
        </v-card>
        <div class="load-more-wrapper" v-if="!articlesComplete">
          <v-btn color="primary" variant="tonal" :loading="loadingArticles" @click="loadMoreArticles">
            加载更多
          </v-btn>
        </div>
      </v-col>

      <v-col md="3" cols="12" class="d-md-block d-none">
        <div class="blog-wrapper">
          <v-card class="blog-card mt-5">
            <div class="author-wrapper">
              <v-avatar size="110">
                <img
                  v-if="blogInfo.websiteConfig.websiteAvatar"
                  class="author-avatar"
                  :src="blogInfo.websiteConfig.websiteAvatar"
                  :alt="blogInfo.websiteConfig.websiteAuthor"
                />
              </v-avatar>
              <div class="author-name">{{ blogInfo.websiteConfig.websiteAuthor }}</div>
              <div class="author-intro">{{ blogInfo.websiteConfig.websiteIntro }}</div>
            </div>
            <div class="blog-info-wrapper">
              <router-link to="/archives" class="blog-info-data">
                <div>文章</div>
                <strong>{{ blogInfo.articleCount }}</strong>
              </router-link>
            </div>
            <div class="card-info-social">
              <a
                v-if="isShowSocial('qq')"
                class="mr-5 iconfont iconqq"
                target="_blank"
                rel="noopener"
                :href="'http://wpa.qq.com/msgrd?v=3&uin=' + blogInfo.websiteConfig.qq + '&site=qq&menu=yes'"
              />
              <a
                v-if="isShowSocial('github')"
                target="_blank"
                rel="noopener"
                :href="blogInfo.websiteConfig.github"
                class="mr-5 iconfont icongithub"
              />
              <a
                v-if="isShowSocial('gitee')"
                target="_blank"
                rel="noopener"
                :href="blogInfo.websiteConfig.gitee"
                class="iconfont icongitee-fill-round"
              />
            </div>
          </v-card>
          <v-card v-if="blogInfo.websiteConfig.websiteNotice" class="blog-card mt-5">
            <div class="web-info-title"><v-icon size="18">$mdi-bell</v-icon> 公告</div>
            <div class="notice">{{ blogInfo.websiteConfig.websiteNotice }}</div>
          </v-card>
        </div>
      </v-col>
    </v-row>
  </div>
</template>

<script>
export default {
  created() {
    this.loadMoreArticles();
  },
  data() {
    return {
      articleList: [],
      nextCursor: null,
      loadingArticles: false,
      articlesComplete: false
    };
  },
  methods: {
    async requestWithRetry(request) {
      for (const delay of [0, 800, 1600]) {
        if (delay) await new Promise(resolve => setTimeout(resolve, delay));
        try {
          return await request({ suppressErrorToast: true, timeout: 5000 });
        } catch {
          // Retry transient API failures without replacing the article list.
        }
      }
      return null;
    },
    async loadMoreArticles() {
      if (this.loadingArticles || this.articlesComplete) return;
      this.loadingArticles = true;
      try {
        const response = await this.requestWithRetry(config => this.$api.article.home({
          ...config,
          params: { cursor: this.nextCursor || undefined, size: 10 }
        }));
        if (!response) return;
        const page = response.data || {};
        const items = Array.isArray(page.items) ? page.items : [];
        this.articleList.push(...items);
        this.nextCursor = page.nextCursor || null;
        this.articlesComplete = items.length === 0 || !page.hasNext;
      } finally {
        this.loadingArticles = false;
      }
    },
    scrollDown() {
      window.scrollTo({ behavior: "smooth", top: document.documentElement.clientHeight });
    }
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    isShowSocial() {
      return social => (this.blogInfo.websiteConfig.socialUrlList || []).includes(social);
    },
    cover() {
      const page = (this.blogInfo.pageList || []).find(item => item.pageLabel === "home");
      return page?.pageCover ? `background: url("${page.pageCover}") center center / cover no-repeat` : "";
    }
  }
};
</script>

<style scoped>
.home-banner {
  position: absolute;
  top: -60px;
  left: 0;
  right: 0;
  height: 100vh;
  background-attachment: fixed;
  text-align: center;
  color: #fff !important;
  animation: header-effect 1s;
}

.banner-container {
  margin-top: 43vh;
  line-height: 1.5;
  color: #eee;
}

.blog-title {
  font-size: 2.5rem;
}

.blog-intro {
  font-size: 1.5rem;
}

.blog-contact {
  display: none;
}

.blog-contact a,
.card-info-social a {
  color: #fff !important;
}

.card-info-social {
  margin-top: 12px;
  line-height: 40px;
  text-align: center;
}

.card-info-social a {
  font-size: 1.5rem;
}

.load-more-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.article-card {
  display: flex;
  align-items: center;
  width: 100%;
  height: 280px;
  margin-top: 20px;
  overflow: hidden;
}

.article-cover {
  width: 45%;
  height: 100%;
  overflow: hidden;
}

.left-card .article-cover {
  order: 0;
  border-radius: 8px 0 0 8px;
}

.right-card .article-cover {
  order: 1;
  border-radius: 0 8px 8px 0;
}

.on-hover {
  transition: transform 0.6s;
}

.article-card:hover .on-hover {
  transform: scale(1.1);
}

.article-wrapper {
  width: 55%;
  padding: 0 2.5rem;
  font-size: 14px;
}

.right-card .article-wrapper {
  order: 0;
}

.article-title-line a {
  font-size: 1.5rem;
  overflow-wrap: anywhere;
}

.article-title-line a:hover {
  color: #8e8cd8;
}

.article-info {
  margin: 0.375rem 0;
  color: #858585;
  font-size: 95%;
  line-height: 2;
}

.top-mark {
  color: #ff7242;
}

.article-content {
  display: -webkit-box;
  overflow: hidden;
  color: #858585;
  line-height: 2;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.home-container {
  max-width: 1200px;
  margin: calc(100vh - 48px) auto 28px;
  padding: 0 5px;
}

.blog-wrapper {
  position: sticky;
  top: 10px;
}

.blog-card {
  padding: 1.25rem 1.5rem;
  line-height: 2;
}

.author-wrapper {
  text-align: center;
}

.author-avatar {
  transition: transform 0.5s;
}

.author-avatar:hover {
  transform: rotate(360deg);
}

.author-name {
  margin-top: 0.625rem;
  font-size: 1.375rem;
}

.author-intro,
.notice {
  font-size: 0.875rem;
}

.blog-info-wrapper {
  display: flex;
  justify-content: center;
  padding: 0.875rem 0;
  text-align: center;
}

.blog-info-data {
  text-decoration: none;
}

.blog-info-data strong {
  font-size: 1.25rem;
}

@media (max-width: 759px) {
  .blog-title {
    font-size: 26px;
  }

  .blog-intro {
    font-size: 1.1rem;
  }

  .blog-contact {
    display: block;
    font-size: 1.25rem;
    line-height: 2;
  }

  .home-container {
    width: 100%;
    margin: calc(100vh - 66px) auto 0;
  }

  .article-card {
    display: block;
    height: auto;
    margin-top: 1rem;
  }

  .article-cover,
  .left-card .article-cover,
  .right-card .article-cover {
    width: 100%;
    height: 230px;
    border-radius: 8px 8px 0 0;
  }

  .article-wrapper,
  .right-card .article-wrapper {
    width: 100%;
    padding: 1.25rem 1.25rem 1.875rem;
  }

  .article-title-line a {
    font-size: 1.25rem;
  }
}

.scroll-down {
  position: absolute;
  bottom: 0;
  width: 100%;
  cursor: pointer;
}

.scroll-down-effects {
  display: inline-block;
  color: #eee !important;
  text-shadow: 0.1rem 0.1rem 0.2rem rgba(0, 0, 0, 0.15);
  animation: scroll-down-effect 1.5s infinite;
}

@keyframes scroll-down-effect {
  0%, 100% { top: 0; opacity: 0.4; }
  50% { top: -16px; opacity: 1; }
}
</style>
