<template>
  <el-card class="main-card website-page">
    <div class="website-header">
      <div>
        <div class="title">网站管理</div>
        <p class="website-description">维护网站名称、介绍、公告和外部链接，让前台信息保持清晰一致。</p>
      </div>
      <div class="website-header-mark"><span></span>站点配置</div>
    </div>

    <el-form class="website-form" label-position="top" :model="websiteConfigForm">
      <div class="website-layout">
        <div class="website-form-main">
          <section class="website-panel">
            <div class="website-panel-heading">
              <div>
                <h2>站点基础信息</h2>
                <p>设置网站在首页和浏览器标题中使用的基础内容。</p>
              </div>
              <span class="website-panel-icon"><AppIcon name="globe" :size="19" /></span>
            </div>

            <div class="website-form-grid">
              <el-form-item label="网站头像" class="website-field-wide">
                <el-upload
                  class="avatar-uploader"
                  aria-label="更换网站头像"
                  :action="$api.admin.uploadConfigImageUrl"
                  :headers="uploadHeaders"
                  :with-credentials="true"
                  :show-file-list="false"
                  :before-upload="beforeUpload"
                  :on-success="handleWebsiteAvatarSuccess"
                  :on-error="uploadError"
                >
                  <span class="avatar-upload-content">
                    <img v-if="websiteConfigForm.websiteAvatar" :src="websiteConfigForm.websiteAvatar" class="avatar" alt="网站头像" />
                    <span v-if="websiteConfigForm.websiteAvatar" class="avatar-edit-hint">更换头像</span>
                    <span v-else class="avatar-placeholder">
                      <AppIcon name="plus" :size="20" />
                      <span>上传网站头像</span>
                    </span>
                  </span>
                </el-upload>
                <p class="field-hint">建议使用正方形图片，展示在网站品牌区域。</p>
              </el-form-item>
              <el-form-item label="网站名称">
                <el-input v-model="websiteConfigForm.websiteName" maxlength="80" show-word-limit placeholder="例如：我的个人博客" />
              </el-form-item>
              <el-form-item label="网站作者">
                <el-input v-model="websiteConfigForm.websiteAuthor" maxlength="50" show-word-limit placeholder="例如：你的名字" />
              </el-form-item>
              <el-form-item label="网站介绍" class="website-field-wide">
                <el-input v-model="websiteConfigForm.websiteIntro" maxlength="200" show-word-limit placeholder="用一句话介绍这个网站" />
              </el-form-item>
            </div>
          </section>

          <section class="website-panel">
            <div class="website-panel-heading">
              <div>
                <h2>站点信息</h2>
                <p>补充公告、创建时间和备案信息。</p>
              </div>
              <span class="website-panel-icon"><AppIcon name="file" :size="19" /></span>
            </div>
            <div class="website-form-grid">
              <el-form-item label="网站公告" class="website-field-wide">
                <el-input v-model="websiteConfigForm.websiteNotice" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="输入想展示给访客的公告" />
              </el-form-item>
              <el-form-item label="创建时间">
                <el-date-picker class="website-date-picker" v-model="websiteConfigForm.websiteCreateTime" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
              </el-form-item>
              <el-form-item label="备案号">
                <el-input v-model="websiteConfigForm.websiteRecordNo" maxlength="80" placeholder="如暂时没有可留空" />
              </el-form-item>
            </div>
          </section>

          <section class="website-panel">
            <div class="website-panel-heading">
              <div>
                <h2>社交链接</h2>
                <p>选择需要展示的平台，再填写对应地址。</p>
              </div>
              <span class="website-panel-icon"><AppIcon name="settings" :size="19" /></span>
            </div>
            <el-form-item label="显示平台">
              <el-checkbox-group v-model="websiteConfigForm.socialUrlList" class="social-options">
                <el-checkbox value="qq">QQ</el-checkbox>
                <el-checkbox value="github">GitHub</el-checkbox>
                <el-checkbox value="gitee">Gitee</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <div class="social-link-grid">
              <el-form-item v-if="websiteConfigForm.socialUrlList.includes('qq')" label="QQ 号码">
                <el-input v-model="websiteConfigForm.qq" placeholder="输入 QQ 号码" />
              </el-form-item>
              <el-form-item v-if="websiteConfigForm.socialUrlList.includes('github')" label="GitHub 地址">
                <el-input v-model="websiteConfigForm.github" placeholder="https://github.com/..." />
              </el-form-item>
              <el-form-item v-if="websiteConfigForm.socialUrlList.includes('gitee')" label="Gitee 地址">
                <el-input v-model="websiteConfigForm.gitee" placeholder="https://gitee.com/..." />
              </el-form-item>
            </div>
          </section>
        </div>

        <aside class="website-side-column">
          <section class="website-preview-card">
            <div class="preview-card-heading">
              <span>网站预览</span>
              <span class="preview-live"><i></i>实时</span>
            </div>
            <div class="preview-brand">
              <span class="preview-avatar">
                <img v-if="websiteConfigForm.websiteAvatar" :src="websiteConfigForm.websiteAvatar" alt="网站头像预览" />
                <span v-else>{{ previewInitial }}</span>
              </span>
              <span>
                <strong>{{ websiteConfigForm.websiteName || "未命名网站" }}</strong>
                <small>{{ websiteConfigForm.websiteAuthor || "网站作者" }}</small>
              </span>
            </div>
            <p class="preview-intro">{{ websiteConfigForm.websiteIntro || "填写网站介绍后，会在这里看到前台品牌信息。" }}</p>
            <div class="preview-notice">
              <span>公告</span>
              <strong>{{ websiteConfigForm.websiteNotice || "暂未设置网站公告" }}</strong>
            </div>
          </section>
          <section class="website-tip-card">
            <span class="tip-icon"><AppIcon name="info" :size="18" /></span>
            <div>
              <strong>小提示</strong>
              <p>修改内容后点击底部保存，前台会在下一次加载时使用最新配置。</p>
            </div>
          </section>
        </aside>
      </div>

      <div class="website-form-actions">
        <el-button type="primary" :loading="saving" @click="updateWebsiteConfig">保存网站设置</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";
import { getCsrfHeaders } from "../../../../shared/http/csrf";
import { compressImageForUpload } from "../../utils/imageUpload";

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
  components: { AppIcon },
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
    beforeUpload(file) {
      return compressImageForUpload(file, this.config.UPLOAD_SIZE);
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
    previewInitial() {
      return (this.websiteConfigForm.websiteName || "t").slice(0, 1).toUpperCase();
    },
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

.website-section + .website-section {
  padding-top: 1.5rem;
  margin-top: 1.5rem;
  border-top: 1px solid #e5e5ea;
}

.website-section-heading {
  margin-bottom: 1rem;
  color: #1d1d1f;
  font-size: 14px;
  font-weight: 700;
}

.avatar-uploader {
  display: inline-flex;
}

.avatar-uploader :deep(.el-upload) {
  position: relative;
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

.avatar-edit-hint {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 5px 4px;
  color: #fff;
  font-size: 11px;
  text-align: center;
  background: rgba(29, 29, 31, 0.62);
}

.website-date-picker {
  width: 320px;
  max-width: 100%;
}

.website-form-actions {
  display: flex;
  justify-content: flex-start;
  padding-top: 0.5rem;
  margin-left: 100px;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .website-form {
    margin-top: 1.25rem;
    padding-bottom: 0.5rem;
  }

  .website-section + .website-section {
    padding-top: 1.25rem;
    margin-top: 1.25rem;
  }

  .website-form :deep(.el-form-item) {
    display: block;
    margin-bottom: 1rem;
  }

  .website-form :deep(.el-form-item__label) {
    display: block;
    width: 100% !important;
    height: auto;
    padding: 0;
    margin: 0 0 0.4rem;
    overflow: visible;
    line-height: 1.5;
    text-align: left;
    white-space: nowrap;
  }

  .website-form :deep(.el-form-item__content) {
    width: 100%;
    min-width: 0;
    margin-left: 0 !important;
  }

  .website-form-actions {
    position: static;
    justify-content: stretch;
    padding: 0;
    margin: 1.25rem 0 0;
    background: transparent;
    border-top: 0;
  }

  .website-form-actions .el-button {
    width: 100%;
  }
}

/* Redesigned site configuration surface */
.website-form {
  max-width: none;
  margin-top: 24px;
}

.website-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.website-header .title {
  margin-bottom: 8px;
}

.website-description {
  max-width: 700px;
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.website-header-mark {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 7px 10px;
  color: var(--admin-text-secondary);
  font-size: 11px;
  background: #f7faff;
  border: 1px solid #d9e8f9;
  border-radius: 999px;
}

.website-header-mark span,
.preview-live i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--admin-green);
  box-shadow: 0 0 0 3px rgba(52, 199, 89, 0.12);
}

.website-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 270px;
  align-items: start;
  gap: 20px;
}

.website-form-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.website-panel,
.website-preview-card,
.website-tip-card {
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(29, 29, 31, 0.035);
}

.website-panel {
  padding: 22px;
}

.website-panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid #eef0f4;
}

.website-panel-heading h2 {
  margin: 0 0 5px;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.website-panel-heading p {
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.website-panel-icon {
  display: inline-grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  color: var(--admin-blue);
  place-items: center;
  background: var(--admin-blue-soft);
  border-radius: 11px;
}

.website-form-grid,
.social-link-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.website-field-wide {
  grid-column: 1 / -1;
}

.website-form :deep(.el-form-item) {
  margin-bottom: 17px;
}

.website-form :deep(.el-form-item__label) {
  display: block;
  width: auto !important;
  height: auto;
  padding: 0 0 7px;
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
  text-align: left;
}

.website-form :deep(.el-input__wrapper),
.website-form :deep(.el-textarea__inner),
.website-form :deep(.el-select__wrapper),
.website-form :deep(.el-date-editor.el-input__wrapper) {
  background: #fbfcff;
  border-color: #dfe5ee;
  border-radius: 11px;
}

.website-form :deep(.el-input__wrapper),
.website-form :deep(.el-select__wrapper),
.website-form :deep(.el-date-editor.el-input__wrapper) {
  min-height: 44px;
}

.website-form :deep(.el-input__wrapper:hover),
.website-form :deep(.el-textarea__inner:hover),
.website-form :deep(.el-select__wrapper:hover),
.website-form :deep(.el-date-editor.el-input__wrapper:hover) {
  border-color: #a9c9ee;
}

.website-form :deep(.el-textarea__inner) {
  padding: 11px 13px;
}

.field-hint {
  margin: 8px 0 0;
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.avatar-uploader {
  display: inline-flex;
}

.avatar-upload-content,
.avatar-uploader :deep(.el-upload) {
  position: relative;
  display: grid;
  width: 108px;
  height: 108px;
  place-items: center;
  overflow: hidden;
  background: #f4f8ff;
  border: 1px dashed #b9d3f2;
  border-radius: 16px;
  cursor: pointer;
  transition: border-color 180ms ease, background 180ms ease;
}

.avatar-uploader :deep(.el-upload:hover),
.avatar-uploader:hover .avatar-upload-content {
  background: #edf6ff;
  border-color: var(--admin-blue);
}

.avatar {
  display: block;
  width: 108px;
  height: 108px;
  object-fit: cover;
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 5px;
  color: var(--admin-blue);
  font-size: 11px;
}

.avatar-edit-hint {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 6px 4px;
  color: #fff;
  font-size: 11px;
  text-align: center;
  background: rgba(29, 29, 31, 0.66);
}

.website-date-picker {
  width: 100%;
  max-width: 100%;
}

.social-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  min-height: 34px;
}

.social-options :deep(.el-checkbox) {
  margin-right: 0;
}

.website-side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.website-preview-card {
  padding: 20px;
  background: linear-gradient(160deg, #f8fbff 0%, #fff 58%);
}

.preview-card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 700;
}

.preview-live {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #218c3a;
  font-size: 10px;
  font-weight: 600;
}

.preview-live i {
  display: inline-block;
  width: 6px;
  height: 6px;
}

.preview-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 24px 0 16px;
}

.preview-avatar {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  overflow: hidden;
  color: var(--admin-blue);
  font-size: 18px;
  font-weight: 750;
  background: var(--admin-blue-soft);
  border-radius: 13px;
}

.preview-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-brand > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.preview-brand strong {
  overflow: hidden;
  color: var(--admin-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-brand small {
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.preview-intro {
  min-height: 54px;
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.preview-notice {
  padding-top: 15px;
  margin-top: 15px;
  border-top: 1px solid #e7eef7;
}

.preview-notice span {
  display: block;
  margin-bottom: 6px;
  color: var(--admin-blue);
  font-size: 10px;
  font-weight: 700;
}

.preview-notice strong {
  display: block;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
}

.website-tip-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px;
  background: #fffaf1;
  border-color: #f7e6c3;
}

.tip-icon {
  display: inline-grid;
  width: 31px;
  height: 31px;
  flex: 0 0 auto;
  color: #b87508;
  place-items: center;
  background: #fff0ce;
  border-radius: 10px;
}

.website-tip-card strong {
  display: block;
  margin: 1px 0 4px;
  color: #7e570d;
  font-size: 12px;
}

.website-tip-card p {
  margin: 0;
  color: #9b7532;
  font-size: 11px;
  line-height: 1.6;
}

.website-form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--admin-border);
}

.website-form-actions .el-button {
  min-width: 132px;
}

@media (max-width: 1100px) {
  .website-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .website-side-column {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .website-header {
    flex-direction: column;
  }

  .website-header-mark {
    display: none;
  }

  .website-panel {
    padding: 18px;
  }

  .website-form-grid,
  .social-link-grid,
  .website-side-column {
    grid-template-columns: minmax(0, 1fr);
  }

  .website-form-actions {
    justify-content: stretch;
  }

  .website-form-actions .el-button {
    width: 100%;
  }
}
</style>
