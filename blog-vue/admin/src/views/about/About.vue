<template>
  <el-card class="main-card about-page">
    <div class="title">{{ $route.name }}</div>

    <div class="about-intro">
      <div>
        <p class="about-kicker">CONTENT / MARKDOWN</p>
        <p class="about-description">这里只编辑 Markdown 源码，保存后前台页面会负责渲染。</p>
      </div>
      <span class="about-source-status"><span class="about-source-dot" />源码模式</span>
    </div>

    <section class="about-editor-section" aria-label="Markdown 编辑器">
      <div class="about-section-heading">
        <div>
          <h2>Markdown 源码</h2>
          <p>直接编写标题、列表、链接、代码块等 Markdown 内容。</p>
        </div>
        <span class="about-section-note">纯源码</span>
      </div>
      <md-editor
        ref="md"
        class="about-editor"
        :preview="false"
        :toolbars="editorToolbars"
        placeholder="开始编写关于页面..."
        @onUploadImg="uploadImg"
        @onSave="updateAbout"
        v-model="aboutContent"
      />
    </section>

    <div class="about-actions">
      <el-button type="primary" size="medium" :loading="saving" @click="updateAbout">
        保存关于页
      </el-button>
    </div>
  </el-card>
</template>

<script>
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import * as imageConversion from "image-conversion";

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
  components: { MdEditor },
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
  data() {
    return {
      aboutContent: "",
      isMobileViewport: false,
      saving: false,
      viewportQuery: null
    };
  },
  computed: {
    editorToolbars() {
      return this.isMobileViewport ? mobileEditorToolbars : undefined;
    }
  },
  methods: {
    syncViewport() {
      this.isMobileViewport = this.viewportQuery.matches;
    },
    getAbout() {
      this.$api.admin.about().then(data => {
        this.aboutContent = data.data || "";
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
.about-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: -8px 0 18px;
}

.about-kicker {
  margin: 0 0 6px;
  color: var(--admin-blue);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.about-description,
.about-section-heading p {
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.about-source-status,
.about-section-note {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  padding: 7px 10px;
  color: var(--admin-blue);
  font-size: 11px;
  background: var(--admin-blue-soft);
  border: 1px solid rgba(0, 113, 227, 0.16);
  border-radius: 999px;
}

.about-source-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--admin-blue);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.12);
}

.about-editor-section {
  padding: 18px;
  border: 1px solid var(--admin-border);
  border-radius: 16px;
}

.about-editor-section {
  background: var(--admin-surface);
}

.about-section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.about-section-heading h2 {
  margin: 0 0 5px;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.about-section-note {
  padding: 5px 9px;
  font-size: 10px;
  white-space: nowrap;
}

.about-editor {
  width: 100%;
  height: clamp(360px, 56vh, 620px);
  overflow: hidden;
  border: 1px solid var(--admin-border);
  border-radius: 12px;
}

.about-editor :deep(.md-editor-toolbar-wrapper) {
  border-radius: 12px 12px 0 0;
}

.about-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .about-intro {
    align-items: flex-start;
    flex-direction: column;
  }

  .about-editor-section {
    padding: 14px;
  }

  .about-editor {
    height: min(500px, calc(100dvh - 360px));
    min-height: 320px;
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
    border-top: 1px solid var(--admin-border);
  }

  .about-actions .el-button {
    width: 100%;
  }
}
</style>
