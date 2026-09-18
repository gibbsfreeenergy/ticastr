<template>
  <el-card class="main-card article-editor-page">
    <div class="title">{{ $route.name }}</div>

    <div v-if="metadataLoading" class="editor-state" role="status" aria-live="polite">
      正在加载文章信息…
    </div>
    <div v-else-if="metadataError" class="editor-state editor-state-error" role="alert">
      <h2>文章信息加载失败</h2>
      <p>{{ metadataError.message }}</p>
      <el-button type="primary" @click="loadArticle">重试</el-button>
    </div>

    <template v-else>
      <div class="article-title-container">
        <el-input
          v-model="article.articleTitle"
          size="medium"
          placeholder="输入文章标题"
          aria-label="文章标题"
        />
        <el-button
          v-if="article.id == null || article.status === 3"
          type="warning"
          size="medium"
          class="save-btn"
          :loading="saveLoading"
          :disabled="!articleContent.trim()"
          @click="saveArticleDraft"
        >
          保存草稿
        </el-button>
        <el-button
          type="primary"
          size="medium"
          :loading="publishLoading"
          :disabled="publishLoading || contentLoading"
          @click="openModel"
        >
          发布文章
        </el-button>
      </div>

      <div v-if="contentLoading" class="editor-state" role="status" aria-live="polite">
        正在加载 Markdown 内容…
      </div>
      <div v-else-if="contentError" class="editor-state editor-state-error" role="alert">
        <p>{{ contentError.message }}</p>
        <el-button type="primary" @click="retryContent">重试正文</el-button>
      </div>
      <template v-else>
        <MobileEditorMode v-model="editorMode" />
        <md-editor
          ref="md"
          class="article-editor"
          :preview="editorPreview"
          :preview-only="editorPreviewOnly"
          :toolbars="editorToolbars"
          placeholder="开始编写文章内容..."
          v-model="articleContent"
          aria-label="文章 Markdown 编辑器"
          @onChange="contentChanged"
          @onUploadImg="uploadImg"
        />
      </template>

      <div class="editor-toolbar" aria-live="polite">
        <span>{{ editorStatusText }}</span>
        <span v-if="lastSavedAt">最近保存：{{ dateTime(lastSavedAt) }}</span>
        <span v-if="contentVersion">当前版本：v{{ contentVersion }}</span>
        <el-button
          size="small"
          :loading="saveLoading"
          :disabled="!article.id || !articleContent.trim() || editorStatus === 'saving'"
          @click="saveContentNow"
        >
          立即保存
        </el-button>
        <el-button
          size="small"
          class="full-preview-btn"
          :disabled="!articleContent.trim()"
          @click="previewVisible = true"
        >
          预览
        </el-button>
        <el-button
          v-if="article.id"
          size="small"
          :loading="loadingVersions"
          @click="loadContentVersions"
        >
          内容历史
        </el-button>
      </div>
      <div v-if="saveError" class="editor-error" role="alert">
        <span>{{ saveError.message }}</span>
        <el-button v-if="saveError.kind !== 'conflict'" link type="primary" @click="saveContentNow">
          重试保存
        </el-button>
        <el-button v-else link type="primary" @click="reloadAfterConflict">
          放弃本地草稿并加载服务器版本
        </el-button>
      </div>
    </template>

    <ArticleEditorForm
      v-model="addOrEdit"
      :article="article"
      :type-list="typeList"
      :before-upload="beforeUpload"
      :upload-request="uploadCoverRequest"
      @save="saveOrUpdateArticle"
      @update-article="updateArticle"
    />

    <el-dialog v-model="previewVisible" title="文章预览" width="80%" top="5vh">
      <ArticlePreview :markdown="articleContent" />
    </el-dialog>

    <ArticleVersionDialog
      v-model="versionsVisible"
      :versions="contentVersions"
      :current-version="contentVersion"
      :has-next="versionsHasNext"
      :loading="loadingVersions"
      @restore="restoreContentVersion"
      @next="loadNextVersions"
    />
  </el-card>
</template>

<script>
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import * as imageConversion from "image-conversion";
import { normalizeHttpError } from "../../../../shared/api/error";
import { createArticleEditorState } from "./articleEditorState";
import { resolveArticleId } from "./articleRoute";
import ArticleEditorForm from "./ArticleEditorForm.vue";
import ArticlePreview from "./ArticlePreview.vue";
import ArticleVersionDialog from "./ArticleVersionDialog.vue";
import MobileEditorMode from "../../components/MobileEditorMode.vue";

const mobileEditorToolbars = [
  "bold",
  "underline",
  "italic",
  "-",
  "title",
  "quote",
  "unorderedList",
  "orderedList",
  "-",
  "link",
  "image",
  "revoke",
  "next"
];

const newArticle = moment => ({
  id: null,
  articleTitle: moment(new Date()).format("YYYY-MM-DD"),
  articleCover: "",
  originalUrl: "",
  isTop: 0,
  type: 1,
  status: 1
});

export default {
  name: "ArticleEditorPage",
  components: {
    MdEditor,
    ArticleEditorForm,
    ArticlePreview,
    ArticleVersionDialog,
    MobileEditorMode
  },
  beforeRouteLeave(to, from, next) {
    if (!this.editor?.state.dirty || this.leaveConfirmed) {
      next();
      return;
    }
    if (window.confirm("当前文章还有未保存内容，确定离开吗？")) {
      this.leaveConfirmed = true;
      next();
    } else {
      next(false);
    }
  },
  created() {
    this.editor = createArticleEditorState({
      api: this.$api,
      articleId: this.routeArticleId,
      debounceMs: 2000,
      onChange: this.syncEditorState
    });
    if (this.routeArticleId) this.loadArticle();
    else this.restoreLocalDraft();
  },
  mounted() {
    this.viewportQuery = window.matchMedia("(max-width: 900px), (hover: none) and (pointer: coarse)");
    this.syncViewport();
    if (this.viewportQuery.addEventListener) this.viewportQuery.addEventListener("change", this.syncViewport);
    else this.viewportQuery.addListener(this.syncViewport);
  },
  beforeUnmount() {
    if (this.viewportQuery) {
      if (this.viewportQuery.removeEventListener) this.viewportQuery.removeEventListener("change", this.syncViewport);
      else this.viewportQuery.removeListener(this.syncViewport);
    }
    this.persistLocalDraft();
    this.editor?.dispose();
  },
  data() {
    return {
      editor: null,
      article: newArticle(this.$moment),
      articleContent: "",
      typeList: [
        { type: 1, desc: "原创" },
        { type: 2, desc: "转载" },
        { type: 3, desc: "翻译" }
      ],
      addOrEdit: false,
      previewVisible: false,
      metadataLoading: Boolean(resolveArticleId(this.$route)),
      contentLoading: Boolean(resolveArticleId(this.$route)),
      metadataError: null,
      contentError: null,
      saveError: null,
      contentVersion: null,
      lastSavedAt: null,
      editorStatus: "loading",
      contentVersions: [],
      versionsCursor: null,
      versionsHasNext: false,
      versionsVisible: false,
      loadingVersions: false,
      saveLoading: false,
      publishLoading: false,
      leaveConfirmed: false,
      editorMode: "edit",
      isMobileViewport: false,
      viewportQuery: null
    };
  },
  computed: {
    routeArticleId() {
      return resolveArticleId(this.$route);
    },
    editorStatusText() {
      const labels = {
        loading: "正在加载",
        ready: "已保存",
        dirty: "有未保存修改（2 秒后自动保存）",
        saving: "正在保存",
        conflict: "版本冲突：本地草稿未被覆盖",
        error: "保存失败"
      };
      return labels[this.editorStatus] || "待编辑";
    },
    editorPreview() {
      return !this.isMobileViewport || this.editorMode === "preview";
    },
    editorPreviewOnly() {
      return this.isMobileViewport && this.editorMode === "preview";
    },
    editorToolbars() {
      return this.isMobileViewport ? mobileEditorToolbars : undefined;
    }
  },
  methods: {
    syncViewport() {
      this.isMobileViewport = this.viewportQuery.matches;
      if (!this.isMobileViewport) this.editorMode = "edit";
    },
    syncEditorState(state) {
      this.editorStatus = state.status;
      this.articleContent = state.markdown;
      this.contentVersion = state.version;
      this.lastSavedAt = state.lastSavedAt;
      this.saveError = state.saveError;
      if (state.metadata) this.article = this.mergeArticleMetadata(state.metadata);
    },
    mergeArticleMetadata(metadata) {
      return {
        ...this.article,
        ...metadata,
        articleContent: undefined
      };
    },
    async loadArticle() {
      this.metadataLoading = true;
      this.contentLoading = true;
      this.metadataError = null;
      this.contentError = null;
      try {
        await this.editor.load(this.routeArticleId);
        this.metadataLoading = false;
        this.contentLoading = false;
        this.article = this.mergeArticleMetadata(this.editor.state.metadata);
        this.articleContent = this.editor.state.markdown;
      } catch (error) {
        const normalized = normalizeHttpError(error);
        this.metadataLoading = !this.editor.state.metadata;
        this.contentLoading = false;
        if (this.editor.state.metadata) this.contentError = normalized;
        else this.metadataError = normalized;
      }
    },
    async retryContent() {
      this.contentLoading = true;
      this.contentError = null;
      try {
        await this.editor.loadContent();
        this.articleContent = this.editor.state.markdown;
      } catch (error) {
        this.contentError = normalizeHttpError(error);
      } finally {
        this.contentLoading = false;
      }
    },
    contentChanged(value) {
      if (!this.editor || value === this.editor.state.markdown) return;
      this.editor.setMarkdown(value);
    },
    validateDraft({ requirePublishFields = false } = {}) {
      if (!this.article.articleTitle.trim()) {
        this.$message.error("文章标题不能为空");
        return false;
      }
      if (!this.articleContent.trim()) {
        this.$message.error("文章内容不能为空");
        return false;
      }
      if (requirePublishFields && !this.article.articleCover.trim()) {
        this.$message.error("文章封面不能为空");
        return false;
      }
      return true;
    },
    articlePayload(status) {
      return {
        id: this.article.id,
        articleTitle: this.article.articleTitle,
        articleCover: this.article.articleCover,
        originalUrl: this.article.originalUrl,
        isTop: this.article.isTop,
        type: this.article.type,
        status
      };
    },
    async saveMetadata(status) {
      const response = await this.$api.article.save(this.articlePayload(status));
      if (!response?.flag || !response.data) {
        throw new Error(response?.message || "文章元数据保存失败");
      }
      this.article.id = response.data;
      this.editor.setArticleId(response.data);
      return response;
    },
    async ensureArticleId() {
      if (this.article.id != null) return;
      await this.saveMetadata(3);
      this.editor.setMarkdown(this.articleContent);
    },
    async saveContentNow() {
      if (!this.articleContent.trim()) return;
      this.saveLoading = true;
      try {
        await this.ensureArticleId();
        if (this.editor.state.markdown !== this.articleContent) {
          this.editor.setMarkdown(this.articleContent);
        }
        await this.editor.saveNow({ force: true });
      } catch (error) {
        this.saveError = normalizeHttpError(error);
        this.$message.error(this.saveError.message);
        throw error;
      } finally {
        this.saveLoading = false;
      }
    },
    async saveArticleDraft() {
      if (!this.validateDraft()) return;
      this.saveLoading = true;
      try {
        await this.saveMetadata(3);
        if (this.editor.state.markdown !== this.articleContent) this.editor.setMarkdown(this.articleContent);
        await this.editor.saveNow({ force: true });
        this.$notify.success({ title: "成功", message: "保存草稿成功" });
      } catch (error) {
        this.$notify.error({ title: "失败", message: normalizeHttpError(error).message });
      } finally {
        this.saveLoading = false;
      }
    },
    openModel() {
      if (!this.validateDraft()) return;
      this.addOrEdit = true;
    },
    async saveOrUpdateArticle() {
      if (!this.validateDraft({ requirePublishFields: true })) return;
      this.publishLoading = true;
      try {
        await this.ensureArticleId();
        if (this.editor.state.markdown !== this.articleContent) this.editor.setMarkdown(this.articleContent);
        await this.editor.saveNow({ force: true });
        await this.saveMetadata(1);
        this.article.status = 1;
        this.addOrEdit = false;
        this.$notify.success({ title: "成功", message: "文章发布成功" });
      } catch (error) {
        this.$notify.error({ title: "失败", message: normalizeHttpError(error).message });
      } finally {
        this.publishLoading = false;
      }
    },
    async loadContentVersions({ append = false } = {}) {
      if (!this.article.id) return;
      this.loadingVersions = true;
      try {
        const response = await this.$api.article.contentVersions(this.article.id, {
          params: {
            size: 20,
            cursor: append ? this.versionsCursor : undefined
          }
        });
        const items = response.data?.items || [];
        this.contentVersions = append ? this.contentVersions.concat(items) : items;
        this.versionsCursor = response.data?.nextCursor || null;
        this.versionsHasNext = Boolean(response.data?.hasNext);
        this.versionsVisible = true;
      } catch (error) {
        this.$message.error(normalizeHttpError(error).message);
      } finally {
        this.loadingVersions = false;
      }
    },
    loadNextVersions() {
      return this.loadContentVersions({ append: true });
    },
    async restoreContentVersion(version) {
      if (!window.confirm("确认恢复到 v" + version.version + "？当前内容会保留为一个新版本。")) return;
      try {
        const response = await this.$api.article.restoreContentVersion(
          this.article.id,
          version.version,
          { expectedVersion: this.contentVersion }
        );
        if (!response?.flag) throw new Error(response?.message || "恢复失败");
        await this.editor.loadContent();
        this.articleContent = this.editor.state.markdown;
        this.contentVersion = this.editor.state.version;
        await this.loadContentVersions();
        this.$message.success("内容版本恢复成功");
      } catch (error) {
        this.$message.error(normalizeHttpError(error).message);
      }
    },
    reloadAfterConflict() {
      return this.retryContent();
    },
    persistLocalDraft() {
      if (this.article.id != null || !this.articleContent.trim()) return;
      sessionStorage.setItem("article", JSON.stringify({
        ...this.articlePayload(3),
        articleContent: this.articleContent
      }));
    },
    restoreLocalDraft() {
      const raw = sessionStorage.getItem("article");
      if (!raw) {
        this.editorStatus = "ready";
        return;
      }
      try {
        const draft = JSON.parse(raw);
        this.article = { ...this.article, ...draft };
        this.articleContent = draft.articleContent || "";
        this.editor.setMarkdown(this.articleContent);
      } catch {
        sessionStorage.removeItem("article");
      }
    },
    async uploadCoverRequest(options) {
      const formData = new FormData();
      formData.append("file", options.file);
      try {
        const response = await this.$api.article.uploadImage(formData);
        if (!response?.flag) throw new Error("封面上传失败");
        options.onSuccess(response);
        this.article.articleCover = response.data;
      } catch (error) {
        options.onError(error);
        this.$message.error(normalizeHttpError(error).message);
      }
    },
    beforeUpload(file) {
      if (file.size / 1024 < this.config.UPLOAD_SIZE) return Promise.resolve(file);
      return imageConversion.compressAccurately(file, this.config.UPLOAD_SIZE);
    },
    async uploadImg(files, callback) {
      try {
        const urls = await Promise.all(Array.from(files).map(file => this.uploadImage(file)));
        callback(urls);
      } catch (error) {
        this.$message.error(normalizeHttpError(error).message);
      }
    },
    async uploadImage(file) {
      let uploadFile = file;
      if (file.size / 1024 >= this.config.UPLOAD_SIZE) {
        const compressed = await imageConversion.compressAccurately(file, this.config.UPLOAD_SIZE);
        uploadFile = new window.File([compressed], file.name, { type: file.type });
      }
      const formData = new FormData();
      formData.append("file", uploadFile);
      const response = await this.$api.article.uploadImage(formData);
      if (!response?.flag) throw new Error("图片上传失败");
      return response.data;
    },
    updateArticle(value) {
      this.article = {
        ...this.article,
        ...value
      };
    }
  }
};
</script>

<style scoped>
.article-title-container {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 2.25rem 0 1.25rem;
}

.article-title-container :deep(.el-input) {
  min-width: 0;
  flex: 1 1 auto;
}

.article-editor {
  width: 100%;
  height: calc(100vh - 260px);
}

.save-btn {
  color: #a96500 !important;
  background: #fff8ea !important;
  border-color: #ffe2ac !important;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding: 0.5rem 0;
  color: #7c8494;
  font-size: 0.8125rem;
}

.editor-state {
  margin: 3rem auto;
  padding: 2rem;
  color: #7c8494;
  text-align: center;
}

.editor-state-error {
  max-width: 700px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.editor-state-error h2 {
  color: #303133;
}

.editor-error {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  color: #c45656;
  font-size: 0.8125rem;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .article-title-container {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .article-title-container :deep(.el-input) {
    flex-basis: 100%;
  }

  .article-title-container > .el-button {
    flex: 1 1 calc(50% - 0.375rem);
    min-width: 0;
  }

  .article-editor {
    height: calc(100vh - 360px);
    height: min(560px, calc(100dvh - 360px));
    min-height: 360px;
  }

  .article-editor :deep(.md-editor-toolbar-wrapper) {
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .article-editor :deep(.md-editor-toolbar) {
    min-width: max-content;
  }

  .editor-toolbar {
    justify-content: flex-start;
  }

  .full-preview-btn {
    display: none;
  }
}
</style>
