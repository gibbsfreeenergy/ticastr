<template>
  <div class="home-page">
    <section class="home-hero" :style="coverStyle">
      <div class="home-hero-fade" aria-hidden="true" />
      <div class="home-hero-inner page-width">
        <div class="hero-copy fade-up">
          <h1>{{ blogInfo.websiteConfig.websiteName }}</h1>
          <p>{{ blogInfo.websiteConfig.websiteIntro }}</p>
          <span class="hero-rule" aria-hidden="true" />
          <div class="hero-socials" aria-label="社交链接">
            <a
              v-if="isShowSocial('qq')"
              class="iconfont iconqq"
              target="_blank"
              rel="noopener"
              aria-label="QQ"
              :href="'http://wpa.qq.com/msgrd?v=3&uin=' + blogInfo.websiteConfig.qq + '&site=qq&menu=yes'"
            />
            <a
              v-if="isShowSocial('github')"
              class="iconfont icongithub"
              target="_blank"
              rel="noopener"
              aria-label="GitHub"
              :href="blogInfo.websiteConfig.github"
            />
            <a
              v-if="isShowSocial('gitee')"
              class="iconfont icongitee-fill-round"
              target="_blank"
              rel="noopener"
              aria-label="Gitee"
              :href="blogInfo.websiteConfig.gitee"
            />
          </div>
        </div>
        <div class="hero-whisper fade-up" style="--delay: 160ms">
          <span>在平凡的日子里</span>
          <span>发现更大的世界</span>
          <i aria-hidden="true" />
        </div>
      </div>
      <button class="scroll-cue" type="button" aria-label="向下阅读" @click="scrollDown">
        <span>向下阅读</span>
        <i aria-hidden="true">↓</i>
      </button>
    </section>

    <section class="home-content page-width">
      <div class="content-heading fade-up">
        <div>
          <span class="heading-number">01</span>
          <h2>最近的文章</h2>
        </div>
        <p>从这里开始，一次安静的阅读。</p>
      </div>

      <div class="home-grid">
        <main class="article-feed">
          <article
            v-for="(item, index) of articleList"
            :key="item.id"
            class="article-card fade-up"
            :style="{ '--delay': `${Math.min(index, 5) * 90}ms` }"
          >
            <router-link class="article-image" :to="'/articles/' + item.id">
              <img :src="item.articleCover" :alt="item.articleTitle" loading="lazy" decoding="async" />
              <span class="image-arrow" aria-hidden="true">↗</span>
            </router-link>
            <div class="article-card-body">
              <div class="article-card-meta">
                <span>0{{ index + 1 }}</span>
                <span v-if="item.isTop == 1" class="top-mark">置顶</span>
                <span>{{ date(item.createTime) }}</span>
              </div>
              <h3>
                <router-link :to="'/articles/' + item.id">{{ item.articleTitle }}</router-link>
              </h3>
              <router-link class="read-link" :to="'/articles/' + item.id">
                <span>阅读全文</span><i aria-hidden="true">→</i>
              </router-link>
            </div>
          </article>

          <div v-if="!articleList.length && !loadingArticles" class="article-empty">
            <span class="empty-mark">✦</span>
            <p>还没有文章，等一阵风把故事带来。</p>
          </div>

          <div class="load-more-wrapper" v-if="!articlesComplete">
            <button class="load-more-button" type="button" :disabled="loadingArticles" @click="loadMoreArticles">
              <span>{{ loadingArticles ? "正在加载" : "加载更多" }}</span>
              <i aria-hidden="true">→</i>
            </button>
          </div>
        </main>

        <aside class="home-aside">
          <section class="author-panel">
            <div class="author-avatar-wrap">
              <v-avatar size="94">
                <img
                  v-if="blogInfo.websiteConfig.websiteAvatar"
                  class="author-avatar"
                  :src="blogInfo.websiteConfig.websiteAvatar"
                  :alt="blogInfo.websiteConfig.websiteAuthor"
                />
              </v-avatar>
            </div>
            <h3>{{ blogInfo.websiteConfig.websiteAuthor }}</h3>
            <span class="author-rule" aria-hidden="true" />
            <p>{{ blogInfo.websiteConfig.websiteIntro }}</p>
            <router-link class="author-link" to="/about">了解更多 <span>↗</span></router-link>
            <div class="author-stat">
              <span>文章</span>
              <strong>{{ blogInfo.articleCount }}</strong>
            </div>
          </section>

          <section v-if="blogInfo.websiteConfig.websiteNotice" class="notice-panel">
            <div class="notice-heading"><span>一则公告</span><i aria-hidden="true">✦</i></div>
            <p>{{ blogInfo.websiteConfig.websiteNotice }}</p>
          </section>
        </aside>
      </div>
    </section>
  </div>
</template>

<script>
export default {
  name: "HomePage",
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
      window.scrollTo({ behavior: "smooth", top: document.querySelector(".home-content")?.offsetTop || window.innerHeight });
    }
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    isShowSocial() {
      return social => (this.blogInfo.websiteConfig.socialUrlList || []).includes(social);
    },
    coverStyle() {
      const page = (this.blogInfo.pageList || []).find(item => item.pageLabel === "home");
      return {
        backgroundImage: `url("${page?.pageCover || "/images/hero-dawn.png"}")`
      };
    }
  }
};
</script>

<style scoped>
.home-page {
  overflow: hidden;
  background: var(--paper);
}

.home-hero {
  position: relative;
  min-height: min(780px, 84vh);
  background-color: #c3c9d0;
  background-position: center;
  background-size: cover;
  color: #fff;
}

.home-hero-fade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(23, 34, 44, 0.04) 38%, rgba(24, 32, 43, 0.22) 82%, var(--paper) 100%);
  pointer-events: none;
}

.home-hero-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: min(780px, 84vh);
  padding-top: 80px;
  padding-bottom: 110px;
}

.hero-copy {
  max-width: 570px;
  animation-delay: var(--delay, 0ms);
  text-shadow: 0 4px 24px rgba(24, 32, 43, 0.18);
}

.hero-copy h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: clamp(58px, 9vw, 122px);
  font-weight: 400;
  letter-spacing: -0.045em;
  line-height: 0.95;
}

.hero-copy p {
  max-width: 480px;
  margin: 28px 0 0 !important;
  font-size: clamp(20px, 2.2vw, 30px);
  letter-spacing: 0.08em;
}

.hero-rule {
  display: block;
  width: 42px;
  height: 2px;
  margin-top: 26px;
  background: rgba(255, 255, 255, 0.85);
}

.hero-socials {
  display: flex;
  gap: 18px;
  margin-top: 26px;
}

.hero-socials a {
  color: #fff;
  font-size: 18px;
  transition: color 180ms ease, transform 180ms ease;
}

.hero-socials a:hover {
  color: var(--blush);
  transform: translateY(-3px);
}

.hero-whisper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 14px 0 14px 22px;
  border-left: 1px solid rgba(255, 255, 255, 0.62);
  color: rgba(255, 255, 255, 0.9);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18px;
  letter-spacing: 0.08em;
  line-height: 1.65;
  text-shadow: 0 3px 16px rgba(24, 32, 43, 0.22);
  animation-delay: var(--delay);
}

.hero-whisper i {
  display: block;
  width: 24px;
  height: 1px;
  margin-top: 9px;
  background: currentColor;
}

.scroll-cue {
  position: absolute;
  bottom: 44px;
  left: 50%;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  color: rgba(255, 255, 255, 0.86);
  background: transparent;
  font-size: 11px;
  letter-spacing: 0.16em;
  transform: translateX(-50%);
}

.scroll-cue i {
  font-size: 20px;
  font-style: normal;
  animation: float-gently 1.8s ease-in-out infinite;
}

.home-content {
  position: relative;
  padding-top: 84px;
  padding-bottom: 110px;
}

.content-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  margin-bottom: 42px;
}

.content-heading > div {
  display: flex;
  align-items: baseline;
  gap: 14px;
}

.heading-number {
  color: var(--sage-deep);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 14px;
}

.content-heading h2 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 500;
  letter-spacing: -0.04em;
}

.content-heading h2::after {
  display: block;
  width: 48px;
  height: 2px;
  margin-top: 12px;
  background: var(--sage);
  content: "";
}

.content-heading p {
  margin: 0 !important;
  color: var(--muted);
  font-size: 13px;
}

.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  gap: 64px;
  align-items: start;
}

.article-feed {
  min-width: 0;
}

.article-card {
  display: grid;
  grid-template-columns: minmax(220px, 43%) minmax(0, 1fr);
  min-height: 250px;
  border-top: 1px solid var(--line);
  animation-delay: var(--delay);
}

.article-card:nth-child(even) {
  grid-template-columns: minmax(0, 1fr) minmax(220px, 43%);
}

.article-card:nth-child(even) .article-image {
  order: 2;
}

.article-card:nth-child(even) .article-card-body {
  order: 1;
}

.article-image {
  position: relative;
  min-height: 250px;
  margin: 24px 0;
  overflow: hidden;
  border-radius: var(--radius-md);
  background: var(--paper-muted);
}

.article-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 600ms cubic-bezier(0.2, 0.7, 0.2, 1), filter 600ms ease;
}

.article-image:hover img {
  filter: saturate(1.08);
  transform: scale(1.055);
}

.image-arrow {
  position: absolute;
  right: 14px;
  bottom: 14px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--ink);
  background: rgba(255, 253, 249, 0.88);
  font-size: 18px;
  opacity: 0;
  transform: translateY(6px);
  transition: opacity 220ms ease, transform 220ms ease;
}

.article-image:hover .image-arrow {
  opacity: 1;
  transform: translateY(0);
}

.article-card-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 32px 0 32px 38px;
}

.article-card:nth-child(even) .article-card-body {
  padding-right: 38px;
  padding-left: 0;
}

.article-card-meta {
  display: flex;
  gap: 13px;
  align-items: center;
  color: var(--muted-light);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 13px;
}

.article-card-meta span + span::before {
  display: inline-block;
  width: 4px;
  height: 4px;
  margin: 0 9px 2px 0;
  border-radius: 50%;
  background: var(--blush);
  content: "";
}

.article-card-meta .top-mark {
  color: var(--sage-deep);
  font-family: inherit;
}

.article-card h3 {
  max-width: 450px;
  margin: 17px 0 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(22px, 2.4vw, 31px);
  font-weight: 500;
  line-height: 1.35;
  letter-spacing: -0.03em;
}

.article-card h3 a {
  transition: color 180ms ease;
}

.article-card h3 a:hover {
  color: var(--sage-deep);
}

.read-link,
.author-link {
  display: inline-flex;
  align-items: center;
  gap: 13px;
  margin-top: 28px;
  color: var(--sage-deep);
  font-size: 13px;
}

.read-link i,
.author-link span {
  font-size: 18px;
  font-style: normal;
  transition: transform 180ms ease;
}

.read-link:hover i,
.author-link:hover span {
  transform: translateX(5px);
}

.load-more-wrapper {
  display: flex;
  justify-content: center;
  padding: 44px 0 0;
}

.load-more-button {
  display: inline-flex;
  align-items: center;
  gap: 18px;
  min-width: 160px;
  justify-content: center;
  padding: 13px 22px;
  border: 1px solid var(--sage);
  border-radius: 999px;
  color: var(--sage-deep);
  background: transparent;
  font-size: 13px;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.load-more-button:hover:not(:disabled) {
  color: #fff;
  background: var(--sage-deep);
  transform: translateY(-2px);
}

.load-more-button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.article-empty {
  padding: 72px 20px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  text-align: center;
}

.empty-mark {
  display: block;
  margin-bottom: 10px;
  color: var(--gold);
  font-size: 26px;
}

.article-empty p {
  margin: 0 !important;
}

.home-aside {
  position: sticky;
  top: 104px;
}

.author-panel {
  position: relative;
  padding: 8px 0 26px;
  border-bottom: 1px solid var(--line);
  text-align: center;
}

.author-avatar-wrap {
  display: inline-flex;
  padding: 8px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
}

.author-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.author-panel h3 {
  margin: 18px 0 7px;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 24px;
  font-weight: 500;
}

.author-rule {
  display: block;
  width: 30px;
  height: 2px;
  margin: 0 auto;
  background: var(--sage);
}

.author-panel p {
  margin: 18px 0 0 !important;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.author-link {
  margin-top: 17px;
}

.author-stat {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 12px;
  margin-top: 28px;
  color: var(--muted);
  font-size: 12px;
}

.author-stat strong {
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22px;
  font-weight: 500;
}

.notice-panel {
  margin-top: 30px;
  padding: 22px 20px;
  border-radius: var(--radius-md);
  background: rgba(184, 181, 216, 0.14);
}

.notice-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--sage-deep);
  font-size: 13px;
}

.notice-heading i {
  color: var(--gold);
  font-style: normal;
}

.notice-panel p {
  margin: 14px 0 0 !important;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.85;
}

@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr) 220px;
    gap: 38px;
  }

  .article-card {
    grid-template-columns: minmax(170px, 40%) minmax(0, 1fr);
  }

  .article-card:nth-child(even) {
    grid-template-columns: minmax(0, 1fr) minmax(170px, 40%);
  }

  .article-card-body {
    padding-left: 24px;
  }

  .article-card:nth-child(even) .article-card-body {
    padding-right: 24px;
  }
}

@media (max-width: 760px) {
  .home-hero,
  .home-hero-inner {
    min-height: 680px;
  }

  .home-hero-inner {
    align-items: flex-end;
    padding-bottom: 124px;
  }

  .hero-copy h1 {
    font-size: clamp(56px, 17vw, 86px);
  }

  .hero-copy p {
    margin-top: 22px !important;
    font-size: 19px;
  }

  .hero-whisper {
    display: none;
  }

  .scroll-cue {
    bottom: 34px;
  }

  .home-content {
    padding-top: 62px;
    padding-bottom: 78px;
  }

  .content-heading {
    display: block;
    margin-bottom: 25px;
  }

  .content-heading p {
    margin-top: 14px !important;
  }

  .home-grid {
    display: block;
  }

  .article-card,
  .article-card:nth-child(even) {
    display: block;
    min-height: 0;
  }

  .article-card:nth-child(even) .article-image,
  .article-card:nth-child(even) .article-card-body {
    order: initial;
  }

  .article-image,
  .article-card:nth-child(even) .article-image {
    min-height: 220px;
    height: 220px;
    margin: 20px 0 0;
  }

  .article-card-body,
  .article-card:nth-child(even) .article-card-body {
    display: block;
    padding: 22px 0 30px;
  }

  .article-card h3 {
    margin-top: 13px;
    font-size: 24px;
  }

  .read-link {
    margin-top: 18px;
  }

  .home-aside {
    position: static;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 24px;
    margin-top: 48px;
  }

  .author-panel {
    padding-bottom: 0;
    border-bottom: 0;
  }

  .notice-panel {
    align-self: start;
    margin-top: 0;
  }
}

@media (max-width: 480px) {
  .home-aside {
    display: block;
  }

  .notice-panel {
    margin-top: 30px;
  }
}
</style>
