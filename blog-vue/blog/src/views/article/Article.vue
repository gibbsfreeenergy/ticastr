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
            <hr />
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
.article-info-container { position: absolute; bottom: 6.25rem; width: 100%; padding: 0 8%; text-align: center; }
.article-title { margin: 20px 0 8px; font-size: 35px; }
.article-info { display: inline-block; font-size: 14px; line-height: 1.9; }
.article-info span { font-size: 95%; }
.article-info i { font-size: 14px; }
.article-category a { color: #fff !important; }
.article-operation { display: flex; align-items: center; }
.tag-container a { display: inline-block; margin: 0.5rem 0.5rem 0.5rem 0; padding: 0 0.75rem; border: 1px solid #49b1f5; border-radius: 1rem; color: #49b1f5 !important; font-size: 12px; line-height: 2; }
.aritcle-copyright { position: relative; margin: 40px 0 10px; padding: 0.625rem 1rem; border: 1px solid #eee; font-size: 0.875rem; line-height: 2; }
.aritcle-copyright span { color: #49b1f5; font-weight: bold; }
.aritcle-copyright a { color: #99a9bf !important; text-decoration: underline !important; }
.article-reward { display: flex; align-items: center; justify-content: center; margin-top: 5rem; }
.like-btn, .like-btn-active { display: inline-block; width: 100px; border: 0; color: #fff; line-height: 36px; font-size: 0.875rem; cursor: pointer; }
.like-btn { background: #969696; }
.like-btn-active { background: #ec7259; }
.reward-btn { position: relative; display: inline-block; width: 100px; margin: 0 1rem; background: #49b1f5; color: #fff; text-align: center; line-height: 36px; font-size: 0.875rem; }
.reward-main { position: absolute; bottom: 40px; left: 0; display: none; width: 100%; padding-bottom: 15px; }
.reward-btn:hover .reward-main, .reward-btn:focus .reward-main { display: block; }
.reward-all { display: inline-flex; width: 320px; margin-left: -110px; padding: 20px 10px 8px; border-radius: 4px; background: #f5f5f5; }
.reward-item { display: inline-flex; flex-direction: column; padding: 0 8px; list-style: none; }
.reward-img { display: block; width: 130px; height: 130px; }
.reward-desc { margin: -5px 0; color: #858585; text-align: center; }
.pagination-post { display: flex; width: 100%; margin-top: 40px; overflow: hidden; background: #000; }
.post { position: relative; width: 50%; height: 150px; overflow: hidden; }
.post-info { position: absolute; top: 50%; width: 100%; padding: 20px 40px; transform: translateY(-50%); line-height: 2; font-size: 14px; }
.post-cover, .recommend-cover { width: 100%; height: 100%; object-fit: cover; opacity: 0.4; }
.post-cover { position: absolute; transition: transform 0.6s; }
.post:hover .post-cover, .recommend-item:hover .recommend-cover { opacity: 0.8; transform: scale(1.1); }
.label { color: #eee; font-size: 90%; }
.post-title { color: #fff; font-weight: 500; }
.recommend-container { margin-top: 40px; }
.recommend-title { margin-bottom: 5px; font-size: 20px; font-weight: bold; line-height: 2; }
.recommend-item { position: relative; display: inline-block; width: calc(33.333% - 6px); height: 200px; margin: 3px; overflow: hidden; background: #000; vertical-align: bottom; }
.recommend-info { position: absolute; top: 50%; width: 100%; padding: 0 20px; transform: translateY(-50%); color: #fff; text-align: center; line-height: 2; font-size: 14px; }
.right-container { padding: 20px 24px; font-size: 14px; }
.right-title { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; font-size: 16.8px; line-height: 2; }
.article-item { display: flex; align-items: center; padding: 6px 0; }
.article-item:not(:last-child) { border-bottom: 1px dashed #f5f5f5; }
.article-item img { width: 100%; height: 100%; object-fit: cover; }
.content { flex: 1; padding-left: 10px; overflow: hidden; word-break: break-all; }
.content-cover { width: 58.8px; height: 58.8px; overflow: hidden; }
.content-title a { font-size: 95%; }
.content-time { color: #858585; font-size: 85%; line-height: 2; }
@media (max-width: 759px) {
  .article-info-container { bottom: 1.3rem; padding: 0 5%; text-align: left; }
  .article-title { font-size: 1.5rem; }
  .pagination-post { display: block; }
  .post { width: 100%; }
  .recommend-item { width: calc(100% - 4px); height: 150px; margin: 2px; }
}
</style>
