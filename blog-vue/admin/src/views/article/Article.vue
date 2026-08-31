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
          type="danger"
          size="medium"
          class="save-btn"
          :loading="saveLoading"
          :disabled="!articleContent.trim()"
          @click="saveArticleDraft"
        >
          保存草稿
        </el-button>
        <el-button
          type="danger"
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
      <md-editor
        v-else
        ref="md"
        v-model="articleContent"
        aria-label="文章 Markdown 编辑器"
        style="height:calc(100vh - 260px)"
        @onChange="contentChanged"
        @onUploadImg="uploadImg"
      />

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
      :category-list="categoryList"
      :tag-list="tagList"
      :category-name="categoryName"
      :tag-name="tagName"
      :type-list="typeList"
      :before-upload="beforeUpload"
      :upload-request="uploadCoverRequest"
      @save="saveOrUpdateArticle"
      @update-article="updateArticle"
      @search-categories="searchCategories"
      @search-tags="searchTags"
      @add-category="addCategory"
      @remove-category="removeCategory"
      @add-tag="addTag"
      @remove-tag="removeTag"
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
import ArticleEditorForm from "./ArticleEditorForm.vue";
import ArticlePreview from "./ArticlePreview.vue";
import ArticleVersionDialog from "./ArticleVersionDialog.vue";

const newArticle = moment => ({
  id: null,
  articleTitle: moment(new Date()).format("YYYY-MM-DD"),
  articleCover: "",
  categoryName: null,
  tagNameList: [],
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
    ArticleVersionDialog
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
  beforeUnmount() {
    this.persistLocalDraft();
    this.editor?.dispose();
  },
  data() {
    return {
      editor: null,
      article: newArticle(this.$moment),
      articleContent: "",
      categoryName: "",
      tagName: "",
      categoryList: [],
      tagList: [],
      typeList: [
        { type: 1, desc: "原创" },
        { type: 2, desc: "转载" },
        { type: 3, desc: "翻译" }
      ],
      addOrEdit: false,
      previewVisible: false,
      metadataLoading: Boolean(this.$route.params.articleId),
      contentLoading: Boolean(this.$route.params.articleId),
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
      leaveConfirmed: false
    };
  },
  computed: {
    routeArticleId() {
      return this.$route.params.articleId || this.$route.path.split("/")[2] || null;
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
    }
  },
  methods: {
    syncEditorState(state) {
      this.editorStatus = state.status;
      this.articleContent = state.markdown;
      this.contentVersion = state.version;
      this.lastSavedAt = state.lastSavedAt;
      this.saveError = state.saveError;
      if (state.metadata) this.article = this.mergeArticleMetadata(state.metadata);
    },
    mergeArticleMetadata(metadata) {
      const tags = metadata.tagNameList
        || (metadata.tagDTOList || []).map(item => item.tagName)
        || [];
      return {
        ...this.article,
        ...metadata,
        tagNameList: tags,
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
      if (requirePublishFields && !this.article.categoryName) {
        this.$message.error("文章分类不能为空");
        return false;
      }
      if (requirePublishFields && this.article.tagNameList.length === 0) {
        this.$message.error("文章标签不能为空");
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
        categoryName: this.article.categoryName,
        tagNameList: this.article.tagNameList,
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
      this.listCategories();
      this.listTags();
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
    listCategories() {
      return this.$api.catalog.categorySearch().then(data => {
        this.categoryList = data.data || [];
      });
    },
    listTags() {
      return this.$api.catalog.tagSearch().then(data => {
        this.tagList = data.data || [];
      });
    },
    searchCategories(keywords, callback) {
      this.$api.catalog.categorySearch({ params: { keywords } })
        .then(data => callback(data.data || []))
        .catch(() => callback([]));
    },
    searchTags(keywords, callback) {
      this.$api.catalog.tagSearch({ params: { keywords } })
        .then(data => callback(data.data || []))
        .catch(() => callback([]));
    },
    addCategory(item) {
      this.article.categoryName = item.categoryName;
    },
    updateArticle(value) {
      this.article = {
        ...this.article,
        ...value,
        tagNameList: [...(value.tagNameList || [])]
      };
    },
    removeCategory() {
      this.article.categoryName = null;
    },
    addTag(item) {
      if (item?.tagName && !this.article.tagNameList.includes(item.tagName)) {
        this.article.tagNameList.push(item.tagName);
      }
    },
    removeTag(item) {
      const index = this.article.tagNameList.indexOf(item);
      if (index >= 0) this.article.tagNameList.splice(index, 1);
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

.save-btn {
  background: #fff;
  color: #f56c6c;
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
</style>
