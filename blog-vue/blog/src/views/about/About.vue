<template>
  <div class="about-page">
    <section class="page-hero" :style="coverStyle">
      <div class="page-hero-fade" aria-hidden="true" />
      <div class="page-width page-hero-content fade-up">
        <span class="page-hero-index">03 / 03</span>
        <h1>关于我</h1>
        <p>{{ blogInfo.websiteConfig.websiteIntro }}</p>
      </div>
    </section>

    <main class="about-main page-width">
      <div class="about-shell">
        <aside class="about-identity">
          <div class="about-avatar-wrap">
            <v-avatar size="132">
              <img class="about-avatar" :src="avatar" :alt="blogInfo.websiteConfig.websiteAuthor" />
            </v-avatar>
          </div>
          <h2>{{ blogInfo.websiteConfig.websiteAuthor }}</h2>
          <span class="about-rule" aria-hidden="true" />
          <p class="about-motto">慢一点，也很好。</p>
          <p class="about-intro">这里是我用来放日常与想法的小角落。关于生活、关于技术，也关于那些细碎而珍贵的时刻。</p>
          <div class="about-socials" aria-label="社交链接">
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
            <a
              v-if="isShowSocial('qq')"
              class="iconfont iconqq"
              target="_blank"
              rel="noopener"
              aria-label="QQ"
              :href="'http://wpa.qq.com/msgrd?v=3&uin=' + blogInfo.websiteConfig.qq + '&site=qq&menu=yes'"
            />
          </div>
        </aside>

        <article
          ref="about"
          class="about-content markdown-body"
          v-safe-html="aboutContent"
        />
      </div>
    </main>
  </div>
</template>

<script>
import { renderMarkdownCode } from "../../utils/markdown";
import { renderMarkdown } from "../../utils/renderMarkdown";
import Clipboard from "clipboard";

export default {
  name: "AboutPage",
  created() {
    this.getAboutContent();
  },
  unmounted() {
    this.clipboard?.destroy();
  },
  data() {
    return {
      aboutContent: "",
      clipboard: null,
      imgList: []
    };
  },
  methods: {
    getAboutContent() {
      this.$api.public.about().then(data => {
        this.markdownToHtml(data);
        this.$nextTick(() => {
          this.clipboard = new Clipboard(".copy-btn");
          this.clipboard.on("success", () => {
            this.$toast({ type: "success", message: "复制成功" });
          });
          const imgList = this.$refs.about?.getElementsByTagName("img") || [];
          this.imgList = [...imgList].map(image => image.src);
          [...imgList].forEach(image => {
            image.addEventListener("click", () => this.previewImg(image.currentSrc || image.src));
          });
        });
      });
    },
    markdownToHtml(data) {
      this.aboutContent = renderMarkdown(data.data || "", {
        highlight: renderMarkdownCode
      });
    },
    previewImg(img) {
      this.$imagePreview({ images: this.imgList, index: this.imgList.indexOf(img) });
    }
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    avatar() {
      return this.blogInfo.websiteConfig.websiteAvatar;
    },
    isShowSocial() {
      return social => (this.blogInfo.websiteConfig.socialUrlList || []).includes(social);
    },
    coverStyle() {
      const page = (this.blogInfo.pageList || []).find(item => item.pageLabel === "about");
      const pageCover = typeof page?.pageCover === "string" ? page.pageCover.trim() : "";
      return pageCover ? { backgroundImage: `url("${pageCover}")` } : {};
    }
  }
};
</script>

<style scoped>
.about-main {
  padding-top: 90px;
  padding-bottom: 118px;
}

.about-shell {
  display: grid;
  grid-template-columns: 250px minmax(0, 680px);
  justify-content: center;
  gap: 86px;
}

.about-identity {
  align-self: start;
  padding-top: 8px;
  text-align: center;
}

.about-avatar-wrap {
  display: inline-flex;
  padding: 9px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
}

.about-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  transition: transform 600ms ease;
}

.about-avatar:hover {
  transform: rotate(5deg) scale(1.04);
}

.about-identity h2 {
  margin: 22px 0 8px;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 24px;
  font-weight: 500;
}

.about-rule {
  display: block;
  width: 32px;
  height: 2px;
  margin: 0 auto;
  background: var(--sage);
}

.about-motto {
  margin: 18px 0 0 !important;
  color: var(--gold);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18px;
}

.about-intro {
  margin: 14px 0 0 !important;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
  text-align: left;
}

.about-socials {
  display: flex;
  justify-content: center;
  gap: 17px;
  margin-top: 25px;
}

.about-socials a {
  color: var(--sage-deep);
  font-size: 18px;
  transition: color 180ms ease, transform 180ms ease;
}

.about-socials a:hover {
  color: var(--gold);
  transform: translateY(-3px);
}

.about-content {
  min-width: 0;
  color: var(--ink-soft);
  font-size: 16px;
  line-height: 2;
}

.about-content :deep(h1),
.about-content :deep(h2),
.about-content :deep(h3) {
  border-bottom: 0;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.about-content :deep(h1) {
  margin-top: 0;
  font-size: 34px;
}

.about-content :deep(h2) {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
  font-size: 26px;
}

.about-content :deep(h3) {
  font-size: 21px;
}

.about-content :deep(p) {
  margin: 0 0 20px !important;
  color: var(--ink-soft);
  font-size: 16px;
}

.about-content :deep(a) {
  color: var(--sage-deep) !important;
  text-decoration: underline;
  text-decoration-color: rgba(85, 118, 107, 0.35);
  text-underline-offset: 4px;
}

.about-content :deep(blockquote) {
  margin: 30px 0;
  padding: 18px 24px;
  border-left: 2px solid var(--sage);
  color: var(--muted);
  background: rgba(143, 169, 154, 0.1);
}

.about-content :deep(img) {
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.about-content :deep(pre.hljs) {
  position: relative;
  margin: 28px 0;
  padding: 20px 22px !important;
  overflow: auto !important;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-md) !important;
  background: #202a35 !important;
  font-size: 13px !important;
  line-height: 1.8 !important;
}

@media (max-width: 860px) {
  .about-shell {
    grid-template-columns: 210px minmax(0, 1fr);
    gap: 46px;
  }
}

@media (max-width: 700px) {
  .about-main {
    padding-top: 58px;
    padding-bottom: 78px;
  }

  .about-shell {
    display: block;
  }

  .about-identity {
    max-width: 300px;
    margin: 0 auto 54px;
  }

  .about-content :deep(h1) {
    font-size: 29px;
  }

  .about-content :deep(p) {
    font-size: 15px;
  }
}
</style>
