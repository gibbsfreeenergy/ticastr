<template>
  <el-card class="main-card">
    <!-- 标题 -->
    <div class="title">{{ this.$route.name }}</div>
    <div class="operation-container">
      <el-button
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openModel(null)"
      >
        新建页面
      </el-button>
    </div>
    <el-row class="page-container" :gutter="12" v-loading="loading">
      <!-- 空状态 -->
      <el-empty v-if="pageList.length == 0" description="暂无页面" />
      <el-col v-for="item of pageList" :key="item.id" :md="6">
        <div class="page-item">
          <div class="page-operation">
            <el-dropdown @command="handleCommand">
              <button
                type="button"
                class="page-operation-trigger"
                :aria-label="`编辑${item.pageName}`"
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <template #dropdown><el-dropdown-menu>
                <el-dropdown-item :command="'update' + JSON.stringify(item)">
                  <i class="el-icon-edit" />编辑
                </el-dropdown-item>
              </el-dropdown-menu></template>
            </el-dropdown>
          </div>
          <el-image fit="cover" class="page-cover" :src="item.pageCover" />
          <div class="page-name">{{ item.pageName }}</div>
        </div>
      </el-col>
    </el-row>
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
import { MoreFilled } from "@element-plus/icons-vue";
export default {
  components: { MoreFilled },
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
.page-cover {
  position: relative;
  border-radius: 4px;
  width: 100%;
  height: 170px;
}
.page-name {
  text-align: center;
  margin-top: 0.5rem;
}
.page-item {
  position: relative;
  cursor: pointer;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  background: #fbfbfd;
  border: 1px solid #e5e5ea;
  border-radius: 13px;
  overflow: hidden;
}
.page-operation {
  position: absolute;
  z-index: 1000;
  top: 0.75rem;
  right: 0.75rem;
}
.page-operation-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  color: #fff;
  background: rgba(29, 29, 31, 0.68);
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 50%;
  box-shadow: 0 5px 12px rgba(29, 29, 31, 0.16);
  cursor: pointer;
}
.page-operation-trigger:hover,
.page-operation-trigger:focus-visible {
  background: rgba(0, 113, 227, 0.9);
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
</style>
