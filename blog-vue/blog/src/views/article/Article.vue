<template>
  <div class="article-page">
    <div v-if="metadataLoading" class="article-state" role="status" aria-live="polite">正在加载文章信息…</div>
    <div v-else-if="metadataError" class="article-state article-state-error" role="alert">
      <span class="state-mark">✦</span>
      <h1>文章加载失败</h1>
      <p>{{ metadataError.message }}</p>
      <v-btn color="primary" variant="tonal" @click="getArticle">重试</v-btn>
    </div>
    <template v-else>
      <ArticleMeta :article="article" :word-num="wordNum" :read-time="readTime" :cover-style="articleCover" />
      <div class="article-layout page-width">
        <main class="article-reading-column">
          <article class="article-surface">
            <ArticleContent
              ref="contentView"
              :rendered-content="renderedContent"
              :loading="contentLoading"
              :error="contentError"
              @retry="loadContent"
            />
            <ArticleNavigation :article="article" :blog-info="blogInfo" :article-href="articleHref" />
          </article>
        </main>
        <ArticleSidebar :article="article" />
      </div>
    </template>
  </div>
</template>

<script>
import Clipboard from "clipboard";
import tocbot from "tocbot";
import ArticleMeta from "./ArticleMeta";
import ArticleContent from "./ArticleContent";
import ArticleNavigation from "./ArticleNavigation";
import ArticleSidebar from "./ArticleSidebar";
import { hljs } from "../../utils/markdown";
import { applySeo } from "../../utils/seo";
import { createMarkdownRenderer } from "../../utils/renderMarkdown";
import { normalizeMediaUrl } from "../../utils/media";
import { normalizeHttpError } from "../../../../shared/api/error";

const renderMarkdown = createMarkdownRenderer({
  highlight: (code, language) => {
    const languageName = language && hljs.getLanguage(language) ? language : "plaintext";
    if (languageName === "plaintext") {
      return '<pre class="hljs"><code>' + escapeHtml(code) + "</code></pre>";
    }
    return '<pre class="hljs"><code>'
      + hljs.highlight(code, { language: languageName, ignoreIllegals: true }).value
      + "</code></pre>";
  }
});

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

const emptyArticle = () => ({
  id: null,
  articleTitle: "",
  articleCover: "",
  recommendArticleList: [],
  newestArticleList: [],
  lastArticle: { id: 0, articleCover: "", articleTitle: "" },
  nextArticle: { id: 0, articleCover: "", articleTitle: "" }
});

export default {
  name: "ArticlePage",
  components: { ArticleMeta, ArticleContent, ArticleNavigation, ArticleSidebar },
  data() {
    return {
      article: emptyArticle(),
      renderedContent: "",
      wordNum: 0,
      readTime: "",
      contentLoading: false,
      metadataLoading: true,
      metadataError: null,
      contentError: null,
      clipboard: null,
      imageUrls: [],
      imageListeners: [],
      requestGeneration: 0
    };
  },
  created() {
    this.getArticle();
  },
  beforeUnmount() {
    this.disposeContentEnhancements();
  },
  methods: {
    async getArticle() {
      const generation = ++this.requestGeneration;
      this.metadataLoading = true;
      this.metadataError = null;
      this.contentError = null;
      try {
        const response = await this.$api.article.byId(this.$route.params.articleId);
        if (generation !== this.requestGeneration) return;
        if (!response?.flag || !response.data) throw new Error("文章不存在");
        this.article = {
          ...emptyArticle(),
          ...response.data,
          recommendArticleList: response.data.recommendArticleList || [],
          newestArticleList: response.data.newestArticleList || [],
          lastArticle: response.data.lastArticle || emptyArticle().lastArticle,
          nextArticle: response.data.nextArticle || emptyArticle().nextArticle
        };
        applySeo(this.article, {
          siteName: this.blogInfo.websiteConfig.websiteName,
          siteDescription: this.blogInfo.websiteConfig.websiteIntro,
          author: this.blogInfo.websiteConfig.websiteAuthor
        });
        this.metadataLoading = false;
        await this.loadContent(generation);
      } catch (error) {
        if (generation !== this.requestGeneration) return;
        this.metadataError = normalizeHttpError(error);
        this.metadataLoading = false;
      }
    },
    async loadContent(generation = this.requestGeneration) {
      this.contentLoading = true;
      this.contentError = null;
      this.disposeContentEnhancements();
      try {
        const response = await this.$api.article.content(this.$route.params.articleId);
        if (generation !== this.requestGeneration) return;
        this.renderedContent = renderMarkdown(response.data || "");
        await this.$nextTick();
        if (generation !== this.requestGeneration) return;
        const source = this.$refs.contentView?.$refs.article?.textContent || "";
        this.wordNum = source.replace(/\s+/g, "").length;
        this.readTime = Math.max(1, Math.round(this.wordNum / 400)) + "分钟";
        this.installContentEnhancements();
      } catch (error) {
        if (generation === this.requestGeneration) this.contentError = normalizeHttpError(error);
      } finally {
        if (generation === this.requestGeneration) this.contentLoading = false;
      }
    },
    installContentEnhancements() {
      const articleElement = this.$refs.contentView?.$refs.article;
      if (!articleElement) return;
      this.clipboard = new Clipboard(".copy-btn");
      this.clipboard.on("success", () => this.$toast({ type: "success", message: "复制成功" }));
      tocbot.init({
        tocSelector: "#toc",
        contentSelector: ".article-content",
        headingSelector: "h1, h2, h3",
        hasInnerContainers: true
      });
      const images = articleElement.querySelectorAll("img");
      this.imageUrls = [...images].map(image => image.currentSrc || image.src);
      images.forEach(image => {
        const listener = () => this.previewImg(image.currentSrc || image.src);
        image.addEventListener("click", listener);
        this.imageListeners.push({ image, listener });
      });
    },
    disposeContentEnhancements() {
      this.clipboard?.destroy();
      this.clipboard = null;
      this.imageListeners.forEach(({ image, listener }) => image.removeEventListener("click", listener));
      this.imageListeners = [];
      this.imageUrls = [];
      tocbot.destroy();
    },
    previewImg(image) {
      this.$imagePreview({ images: this.imageUrls, index: this.imageUrls.indexOf(image) });
    }
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    articleHref() {
      return typeof window === "undefined" ? "" : window.location.href;
    },
    articleCover() {
      const articleCover = normalizeMediaUrl(this.article.articleCover);
      return articleCover ? { backgroundImage: `url("${articleCover}")` } : {};
    }
  }
};
</script>

<style scoped>
.article-page {
  background: var(--paper);
}

.article-state {
  width: min(680px, calc(100% - 36px));
  margin: 150px auto;
  padding: 70px 24px;
  color: var(--muted);
  text-align: center;
}

.article-state .state-mark {
  color: var(--gold);
  font-size: 28px;
}

.article-state h1 {
  margin: 16px 0 8px;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 30px;
  font-weight: 500;
}

.article-state p {
  margin: 0 0 24px !important;
}

.article-state-error {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--paper-strong);
  box-shadow: var(--shadow-soft);
}

.article-layout {
  display: grid;
  grid-template-columns: minmax(0, 780px) 260px;
  gap: 76px;
  align-items: start;
  padding-top: 86px;
  padding-bottom: 118px;
}

.article-reading-column {
  min-width: 0;
}

.article-surface {
  min-width: 0;
}

@media (max-width: 980px) {
  .article-layout {
    grid-template-columns: minmax(0, 1fr) 220px;
    gap: 40px;
  }
}

@media (max-width: 760px) {
  .article-layout {
    display: block;
    padding-top: 56px;
    padding-bottom: 78px;
  }
}
</style>
