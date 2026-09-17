<template>
  <div>
    <div v-if="metadataLoading" class="article-state" role="status" aria-live="polite">正在加载文章信息…</div>
    <div v-else-if="metadataError" class="article-state article-state-error" role="alert">
      <h1>文章加载失败</h1>
      <p>{{ metadataError.message }}</p>
      <v-btn color="primary" @click="getArticle">重试</v-btn>
    </div>
    <template v-else>
      <ArticleMeta :article="article" :word-num="wordNum" :read-time="readTime" :cover-style="articleCover" />
      <v-row class="article-container">
        <v-col md="9" cols="12">
          <v-card class="article-wrapper">
            <ArticleContent
              ref="contentView"
              :rendered-content="renderedContent"
              :loading="contentLoading"
              :error="contentError"
              @retry="loadContent"
            />
            <ArticleNavigation :article="article" :blog-info="blogInfo" :article-href="articleHref" />
          </v-card>
        </v-col>
        <v-col md="3" cols="12" class="d-md-block d-none">
          <ArticleSidebar :article="article" />
        </v-col>
      </v-row>
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
      return `background: url("${this.article.articleCover || ""}") center center / cover no-repeat`;
    }
  }
};
</script>

<style scoped>
.article-state {
  max-width: 760px;
  margin: 3rem auto;
  padding: 2rem;
  text-align: center;
}

.article-state-error {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}
</style>
