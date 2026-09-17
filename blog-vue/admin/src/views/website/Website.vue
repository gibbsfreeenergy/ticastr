<template>
  <el-card class="main-card">
    <div class="title">网站设置</div>
    <el-form class="website-form" label-width="100px" :model="websiteConfigForm">
      <el-form-item label="网站头像">
        <el-upload
          class="avatar-uploader"
          :action="$api.admin.uploadConfigImageUrl"
          :headers="uploadHeaders"
          :with-credentials="true"
          :show-file-list="false"
          :on-success="handleWebsiteAvatarSuccess"
          :on-error="uploadError"
        >
          <img v-if="websiteConfigForm.websiteAvatar" :src="websiteConfigForm.websiteAvatar" class="avatar" />
          <span v-else class="avatar-placeholder">上传头像</span>
        </el-upload>
      </el-form-item>
      <el-form-item label="网站名称">
        <el-input v-model="websiteConfigForm.websiteName" maxlength="80" show-word-limit />
      </el-form-item>
      <el-form-item label="网站作者">
        <el-input v-model="websiteConfigForm.websiteAuthor" maxlength="50" show-word-limit />
      </el-form-item>
      <el-form-item label="网站介绍">
        <el-input v-model="websiteConfigForm.websiteIntro" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="网站公告">
        <el-input v-model="websiteConfigForm.websiteNotice" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="websiteConfigForm.websiteCreateTime" type="date" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="备案号">
        <el-input v-model="websiteConfigForm.websiteRecordNo" maxlength="80" />
      </el-form-item>
      <el-form-item label="社交链接">
        <el-checkbox-group v-model="websiteConfigForm.socialUrlList">
          <el-checkbox value="qq">QQ</el-checkbox>
          <el-checkbox value="github">GitHub</el-checkbox>
          <el-checkbox value="gitee">Gitee</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item v-if="websiteConfigForm.socialUrlList.includes('qq')" label="QQ 号码">
        <el-input v-model="websiteConfigForm.qq" />
      </el-form-item>
      <el-form-item v-if="websiteConfigForm.socialUrlList.includes('github')" label="GitHub 地址">
        <el-input v-model="websiteConfigForm.github" />
      </el-form-item>
      <el-form-item v-if="websiteConfigForm.socialUrlList.includes('gitee')" label="Gitee 地址">
        <el-input v-model="websiteConfigForm.gitee" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="updateWebsiteConfig">保存设置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script>
import { getCsrfHeaders } from "../../../../shared/http/csrf";

function defaultConfig() {
  return {
    websiteAvatar: "",
    websiteName: "",
    websiteAuthor: "",
    websiteIntro: "",
    websiteNotice: "",
    websiteCreateTime: "",
    websiteRecordNo: "",
    socialUrlList: [],
    qq: "",
    github: "",
    gitee: ""
  };
}

export default {
  name: "Website",
  data() {
    return {
      websiteConfigForm: defaultConfig(),
      saving: false
    };
  },
  created() {
    this.getWebsiteConfig();
  },
  methods: {
    async getWebsiteConfig() {
      const response = await this.$api.admin.websiteConfig();
      this.websiteConfigForm = { ...defaultConfig(), ...(response.data || {}) };
      if (!Array.isArray(this.websiteConfigForm.socialUrlList)) this.websiteConfigForm.socialUrlList = [];
    },
    handleWebsiteAvatarSuccess(response) {
      this.websiteConfigForm.websiteAvatar = response.data;
    },
    uploadError() {
      this.$message.error("图片上传失败，请刷新页面后重试");
    },
    async updateWebsiteConfig() {
      this.saving = true;
      try {
        const response = await this.$api.admin.updateWebsiteConfig(this.websiteConfigForm);
        if (response.flag) this.$message.success(response.message || "网站设置已保存");
        else this.$message.error(response.message);
      } finally {
        this.saving = false;
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
.website-form {
  max-width: 720px;
  margin-top: 2rem;
}

.avatar-uploader {
  display: inline-flex;
}

.avatar-uploader :deep(.el-upload) {
  display: grid;
  width: 120px;
  height: 120px;
  place-items: center;
  overflow: hidden;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
}

.avatar-uploader :deep(.el-upload:hover) {
  border-color: #409eff;
}

.avatar {
  display: block;
  width: 120px;
  height: 120px;
  object-fit: cover;
}

.avatar-placeholder {
  color: #8c939d;
  font-size: 14px;
}
</style>
