<template>
  <el-card class="main-card">
    <div class="title">{{ this.$route.name }}</div>
    <MobileEditorMode v-model="editorMode" />
    <md-editor
      ref="md"
      class="about-editor"
      :preview="editorPreview"
      :preview-only="editorPreviewOnly"
      :toolbars="editorToolbars"
      placeholder="开始编写关于页面..."
      @onUploadImg="uploadImg"
      @onSave="updateAbout"
      v-model="aboutContent"
    />
    <div class="about-actions">
      <el-button
        type="primary"
        size="medium"
        :loading="saving"
        @click="updateAbout"
      >
        保存关于页
      </el-button>
    </div>
  </el-card>
</template>

<script>
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import * as imageConversion from "image-conversion";
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

export default {
  components: { MdEditor, MobileEditorMode },
  created() {
    this.getAbout();
  },
  mounted() {
    this.viewportQuery = window.matchMedia("(max-width: 900px), (hover: none) and (pointer: coarse)");
    this.syncViewport();
    if (this.viewportQuery.addEventListener) this.viewportQuery.addEventListener("change", this.syncViewport);
    else this.viewportQuery.addListener(this.syncViewport);
  },
  beforeUnmount() {
    if (!this.viewportQuery) return;
    if (this.viewportQuery.removeEventListener) this.viewportQuery.removeEventListener("change", this.syncViewport);
    else this.viewportQuery.removeListener(this.syncViewport);
  },
  data: function() {
    return {
      aboutContent: "",
      editorMode: "edit",
      isMobileViewport: false,
      saving: false,
      viewportQuery: null
    };
  },
  computed: {
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
    getAbout() {
      this.$api.admin.about().then(data => {
        this.aboutContent = data.data;
      });
    },
    async uploadImg(files, callback) {
      const urls = await Promise.all(Array.from(files).map(file => this.uploadImage(file)));
      callback(urls);
    },
    async uploadImage(file) {
      const formdata = new FormData();
      let uploadFile = file;
      if (file.size / 1024 >= this.config.UPLOAD_SIZE) {
        const compressedFile = await imageConversion.compressAccurately(file, this.config.UPLOAD_SIZE);
        uploadFile = new window.File([compressedFile], file.name, { type: file.type });
      }
      formdata.append("file", uploadFile);
      const data = await this.$api.admin.uploadImage(formdata);
      return data.data;
    },
    async updateAbout() {
      this.saving = true;
      try {
        const data = await this.$api.admin.updateAbout({
          aboutContent: this.aboutContent
        });
        if (data.flag) {
          this.$notify.success({
            title: "成功",
            message: data.message
          });
        } else {
          this.$notify.error({
            title: "失败",
            message: data.message
          });
        }
      } catch {
        this.$notify.error({ title: "失败", message: "保存失败，请稍后重试" });
      } finally {
        this.saving = false;
      }
    }
  }
};
</script>

<style scoped>
.about-editor {
  width: 100%;
  height: calc(100vh - 250px);
  margin-top: 1.25rem;
}
.about-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  margin-top: 0;
}
@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .about-editor {
    height: calc(100vh - 320px);
    height: min(560px, calc(100dvh - 320px));
    min-height: 360px;
    margin-top: 0;
  }

  .about-editor :deep(.md-editor-toolbar-wrapper) {
    overflow-x: auto;
    scrollbar-width: thin;
  }

  .about-editor :deep(.md-editor-toolbar) {
    min-width: max-content;
  }

  .about-actions {
    position: sticky;
    z-index: 2;
    bottom: 10px;
    justify-content: stretch;
    padding: 12px 0;
    background: rgba(255, 255, 255, 0.96);
    border-top: 1px solid #e5e5ea;
  }

  .about-actions .el-button {
    width: 100%;
  }
}
</style>
