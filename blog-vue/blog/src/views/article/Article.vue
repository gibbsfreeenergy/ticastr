<template>
  <div>
    <div v-if="metadataLoading" class="article-state" role="status" aria-live="polite">正在加载文章信息…</div>
    <div v-else-if="metadataError" class="article-state article-state-error" role="alert">
      <h1>文章加载失败</h1>
      <p>{{ metadataError.message }}</p>
      <v-btn color="primary" @click="getArticle">重试</v-btn>
    </div>
    <template v-else>
      <ArticleMeta :article="article" :word-num="wordNum" :read-time="readTime" :comment-count="commentCount" :cover-style="articleCover" />
      <v-row class="article-container">
        <v-col md="9" cols="12">
          <v-card class="article-wrapper">
            <ArticleContent ref="contentView" :rendered-content="renderedContent" :loading="contentLoading" :error="contentError" @retry="loadContent" />
            <ArticleNavigation :article="article" :blog-info="blogInfo" :article-href="articleHref" :is-like="isLike" @like="like" @share="shareArticle" />
            <hr class="article-divider" />
            <Comment :type="commentType" @get-comment-count="getCommentCount" />
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
import Comment from "../../components/Comment";
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
  categoryId: null,
  categoryName: "",
  tagDTOList: [],
  recommendArticleList: [],
  newestArticleList: [],
  lastArticle: { id: 0, articleCover: "", articleTitle: "" },
  nextArticle: { id: 0, articleCover: "", articleTitle: "" },
  viewsCount: 0,
  likeCount: 0
});

export default {
  name: "ArticlePage",
  components: { Comment, ArticleMeta, ArticleContent, ArticleNavigation, ArticleSidebar },
  data() {
    return {
      article: emptyArticle(),
      articleContent: "",
      renderedContent: "",
      wordNum: 0,
      readTime: "",
      commentType: 1,
      commentCount: 0,
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
        this.article = { ...emptyArticle(), ...response.data };
        this.article.tagDTOList = response.data.tagDTOList || [];
        this.article.recommendArticleList = response.data.recommendArticleList || [];
        this.article.newestArticleList = response.data.newestArticleList || [];
        this.article.lastArticle = response.data.lastArticle || emptyArticle().lastArticle;
        this.article.nextArticle = response.data.nextArticle || emptyArticle().nextArticle;
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
        this.articleContent = response.data || "";
        this.renderedContent = renderMarkdown(this.articleContent);
        await this.$nextTick();
        if (generation !== this.requestGeneration) return;
        this.wordNum = this.deleteHTMLTag(this.articleContent).length;
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
      tocbot.init({ tocSelector: "#toc", contentSelector: ".article-content", headingSelector: "h1, h2, h3", hasInnerContainers: true });
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
    },
    async shareArticle() {
      const url = window.location.href;
      try {
        if (navigator.share) await navigator.share({ title: this.article.articleTitle, url });
        else {
          await navigator.clipboard.writeText(url);
          this.$toast({ type: "success", message: "链接已复制" });
        }
      } catch {
        // Browser share cancellation is not an application error.
      }
    },
    async like() {
      if (!this.$store.state.userId) {
        this.$store.state.loginFlag = true;
        return;
      }
      try {
        const response = await this.$api.article.like(this.article.id);
        if (!response.flag) return;
        const liked = this.$store.state.articleLikeSet.indexOf(this.article.id) !== -1;
        this.article.likeCount = Math.max(0, (this.article.likeCount || 0) + (liked ? -1 : 1));
        this.$store.commit("articleLike", this.article.id);
      } catch {
        this.$toast({ type: "error", message: "点赞失败，请稍后重试" });
      }
    },
    getCommentCount(count) {
      this.commentCount = count;
    },
    deleteHTMLTag(content) {
      return String(content || "").replace(/[#*_>~\[\]()!-]/g, "").replace(/\s+/g, "").trim();
    },
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    articleHref() {
      return typeof window === "undefined" ? "" : window.location.href;
    },
    articleCover() {
      return "background: url(\"" + (this.article.articleCover || "") + "\") center center / cover no-repeat";
    },
    isLike() {
      return this.$store.state.articleLikeSet.indexOf(this.article.id) !== -1 ? "like-btn-active" : "like-btn";
    }
  }
};
</script>

<style scoped>
.article-state { max-width: 760px; margin: 3rem auto; padding: 2rem; text-align: center; }
.article-state-error { background: #fff; border-radius: 12px; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); }
.article-divider { position: relative; margin: 40px auto; border: 2px dashed #d2ebfd; width: calc(100% - 4px); }
</style>
