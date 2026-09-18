<template>
  <el-card class="main-card page-management-card">
    <div class="page-management-header">
      <div class="page-heading">
        <div class="page-eyebrow">SITE PAGES</div>
        <div class="page-title-row">
          <span class="page-title-mark" aria-hidden="true"></span>
          <h1 class="page-title">{{ this.$route.name }}</h1>
        </div>
        <p class="page-description">管理首页、归档与关于页面的封面和标签</p>
      </div>
      <el-button
        class="page-create-button"
        type="primary"
        @click="openModel(null)"
      >
        <el-icon><Plus /></el-icon>
        <span>新建页面</span>
      </el-button>
    </div>

    <div class="page-grid" v-loading="loading">
      <el-empty
        v-if="pageList.length == 0"
        class="page-empty"
        description="暂无页面"
      />
      <article v-for="item of pageList" :key="item.id" class="page-item">
        <div class="page-cover-frame">
          <el-image fit="cover" class="page-cover" :src="item.pageCover" />
          <div class="page-cover-scrim" aria-hidden="true"></div>
          <span class="page-label-badge">{{ item.pageLabel }}</span>
        </div>
        <div class="page-card-body">
          <div class="page-card-content">
            <h2 class="page-name">{{ item.pageName }}</h2>
            <p class="page-card-meta">
              <span class="page-meta-dot" aria-hidden="true"></span>
              页面标签 · {{ item.pageLabel }}
            </p>
          </div>
          <div class="page-operation">
            <el-dropdown @command="handleCommand">
              <button
                type="button"
                class="page-operation-trigger"
                :aria-label="`编辑${item.pageName}`"
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="'update' + JSON.stringify(item)">
                    <el-icon><EditPen /></el-icon>
                    <span>编辑页面</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </article>
    </div>
    <!-- 新增模态框 -->
    <el-dialog v-model="addOrEdit" width="35%" top="10vh">
      <template #header><div class="dialog-title-container">{{ dialogTitle }}</div></template>
      <el-form class="page-form" label-width="80px" size="medium" :model="pageForum">
        <el-form-item label="页面名称">
          <el-input class="page-dialog-input" v-model="pageForum.pageName" />
        </el-form-item>
        <el-form-item label="页面标签">
          <el-input
            class="page-dialog-input"
            v-model="pageForum.pageLabel"
            :disabled="pageForum.id != null"
          />
        </el-form-item>
        <el-form-item label="页面封面">
          <el-upload
            class="upload-cover"
            drag
            :show-file-list="false"
            :action="$api.admin.uploadConfigImageUrl"
            :headers="uploadHeaders"
            :with-credentials="true"
            multiple
            :before-upload="beforeUpload"
            :on-success="uploadCover"
            :on-error="uploadError"
          >
            <i class="el-icon-upload" v-if="pageForum.pageCover == ''" />
            <div class="el-upload__text" v-if="pageForum.pageCover == ''">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <img
              v-else
              class="page-cover-preview"
              :src="pageForum.pageCover"
            />
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="addOrEdit = false">取 消</el-button>
        <el-button type="primary" @click="addOrEditPage">
          确 定
        </el-button>
      </div></template>
    </el-dialog>
  </el-card>
</template>

<script>
import * as imageConversion from "image-conversion";
import { getCsrfHeaders } from "../../../../shared/http/csrf";
import { EditPen, MoreFilled, Plus } from "@element-plus/icons-vue";
export default {
  components: { EditPen, MoreFilled, Plus },
  created() {
    this.listPages();
  },
  data: function() {
    return {
      keywords: "",
      loading: true,
      current: 1,
      size: 8,
      count: 0,
      addOrEdit: false,
      dialogTitle: "新建页面",
      pageForum: {
        id: null,
        pageName: "",
        pageLabel: "",
        pageCover: ""
      },
      pageList: []
    };
  },
  methods: {
    openModel(item) {
      if (item) {
        this.pageForum = JSON.parse(item);
        this.dialogTitle = "修改页面";
      } else {
        this.pageForum = {
          id: null,
          pageName: "",
          pageLabel: "",
          pageCover: ""
        };
        this.dialogTitle = "新建页面";
      }
      this.addOrEdit = true;
    },
    listPages() {
      this.$api.admin.pages().then(data => {
        this.pageList = data.data;
        this.loading = false;
      });
    },
    addOrEditPage() {
      if (this.pageForum.pageName.trim() == "") {
        this.$message.error("页面名称不能为空");
        return false;
      }
      if (this.pageForum.pageLabel.trim() == "") {
        this.$message.error("页面标签不能为空");
        return false;
      }
      const pageLabel = this.pageForum.pageLabel.trim();
      if (!["home", "archive", "about"].includes(pageLabel)) {
        this.$message.error("页面标签只能是 home、archive 或 about");
        return false;
      }
      this.pageForum.pageLabel = pageLabel;
      if (!this.pageForum.pageCover) {
        this.$message.error("页面封面不能为空");
        return false;
      }
      this.$api.admin.savePage(this.pageForum).then(data => {
        if (data.flag) {
          this.$notify.success({
            title: "成功",
            message: data.message
          });
          this.listPages();
        } else {
          this.$notify.error({
            title: "失败",
            message: data.message
          });
        }
      });
      this.addOrEdit = false;
    },
    uploadCover(response) {
      this.pageForum.pageCover = response.data;
    },
    uploadError() {
      this.$message.error("图片上传失败，请刷新页面后重试");
    },
    beforeUpload(file) {
      return new Promise(resolve => {
        if (file.size / 1024 < this.config.UPLOAD_SIZE) {
          resolve(file);
        }
        // 压缩到200KB,这里的200就是要压缩的大小,可自定义
        imageConversion
          .compressAccurately(file, this.config.UPLOAD_SIZE)
          .then(res => {
            resolve(res);
          });
      });
    },
    handleCommand(command) {
      const type = command.substring(0, 6);
      const data = command.substring(6);
      if (type == "update") {
        this.openModel(data);
      }
    }
  },
  computed: {
    uploadHeaders() {
      return getCsrfHeaders();
    }
  }
};
</script>

<style scoped>
.page-management-card {
  --page-card-border: #e7eaf0;
  --page-card-shadow: 0 12px 28px rgba(38, 57, 82, 0.07);
}

.page-management-card :deep(.el-card__body) {
  padding: clamp(1.25rem, 3vw, 2rem);
}

.page-management-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
  margin-bottom: 1.75rem;
}

.page-eyebrow {
  margin-bottom: 0.5rem;
  color: #8a94a6;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  line-height: 1;
}

.page-title-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.page-title-mark {
  display: block;
  width: 0.3rem;
  height: 1.75rem;
  border-radius: 999px;
  background: #0071e3;
}

.page-title {
  margin: 0;
  color: #1d1d1f;
  font-size: clamp(1.35rem, 2vw, 1.75rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.page-description {
  margin: 0.65rem 0 0;
  color: #6e6e73;
  font-size: 0.85rem;
  line-height: 1.5;
}

.page-create-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-width: 8.5rem;
  min-height: 2.75rem;
  padding: 0.7rem 1rem;
  border-radius: 0.75rem;
  box-shadow: 0 8px 18px rgba(0, 113, 227, 0.2);
  font-weight: 600;
  line-height: 1;
  text-align: center;
}

.page-create-button :deep(.el-icon) {
  margin: 0;
}

.page-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1.25rem;
  min-height: 10rem;
}

.page-empty {
  grid-column: 1 / -1;
  width: 100%;
}

.page-item {
  position: relative;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--page-card-border);
  border-radius: 1rem;
  box-shadow: var(--page-card-shadow);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.page-item:hover {
  border-color: #c8ddf8;
  box-shadow: 0 18px 34px rgba(38, 57, 82, 0.12);
  transform: translateY(-3px);
}

.page-cover-frame {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #edf2f8;
}

.page-cover {
  display: block;
  width: 100%;
  height: 100%;
}

.page-cover :deep(.el-image__inner) {
  transition: transform 300ms ease;
}

.page-item:hover .page-cover :deep(.el-image__inner) {
  transform: scale(1.035);
}

.page-cover-scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.08), transparent 48%, rgba(17, 24, 39, 0.24));
  pointer-events: none;
}

.page-label-badge {
  position: absolute;
  bottom: 0.8rem;
  left: 0.85rem;
  max-width: calc(100% - 1.7rem);
  overflow: hidden;
  padding: 0.35rem 0.55rem;
  color: #fff;
  background: rgba(17, 24, 39, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 0.45rem;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
  backdrop-filter: blur(8px);
}

.page-card-body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1rem 1.05rem;
}

.page-card-content {
  min-width: 0;
}

.page-name {
  margin: 0;
  overflow: hidden;
  color: #1d1d1f;
  font-size: 1.05rem;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-card-meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0.45rem 0 0;
  overflow: hidden;
  color: #8a94a6;
  font-size: 0.75rem;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-meta-dot {
  flex: 0 0 auto;
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: #72a7df;
}

.page-operation {
  flex: 0 0 auto;
  padding-top: 0.05rem;
}

.page-operation-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  color: #596579;
  background: #f7f9fc;
  border: 1px solid #e3e8f0;
  border-radius: 0.65rem;
  cursor: pointer;
  transition: color 160ms ease, background 160ms ease, border-color 160ms ease;
}

.page-operation-trigger:hover,
.page-operation-trigger:focus-visible {
  color: #0071e3;
  background: #eaf3ff;
  border-color: #bcd8f7;
  outline: none;
}

.page-dialog-input {
  width: 220px;
  max-width: 100%;
}

.upload-cover {
  width: 360px;
  max-width: 100%;
}

.upload-cover :deep(.el-upload-dragger) {
  width: 100%;
}

.page-cover-preview {
  display: block;
  width: 100%;
  max-height: 180px;
  object-fit: cover;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .page-dialog-input {
    width: 100% !important;
  }

  .upload-cover {
    width: 100%;
  }
}

@media (max-width: 1100px) {
  .page-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .page-management-header {
    align-items: stretch;
    flex-direction: column;
    gap: 1.1rem;
    margin-bottom: 1.35rem;
  }

  .page-description {
    font-size: 0.8rem;
  }

  .page-create-button {
    width: 100%;
  }

  .page-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 1rem;
  }

  .page-card-body {
    padding: 0.9rem 0.9rem 0.95rem;
  }
}
</style>
