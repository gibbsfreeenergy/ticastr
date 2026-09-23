<template>
  <el-card class="main-card settings-card">
    <div class="settings-header">
      <div>
        <div class="title">个人中心</div>
        <p class="settings-description">管理公开资料、头像以及管理员账户的登录安全。</p>
      </div>
      <div class="settings-header-mark"><span></span>账户设置</div>
    </div>

    <el-tabs v-model="activeName" class="settings-tabs">
      <el-tab-pane label="个人资料" name="info">
        <div class="profile-layout">
          <aside class="profile-summary">
            <div class="profile-summary-top"></div>
            <el-upload
              class="profile-avatar-uploader"
              aria-label="更换头像"
              :action="$api.auth.avatarUploadUrl"
              :headers="uploadHeaders"
              :with-credentials="true"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :on-success="updateAvatar"
              :on-error="uploadError"
            >
              <span class="profile-avatar-frame">
                <img v-if="avatar" :src="avatar" class="profile-avatar" alt="当前头像" />
                <span v-else class="profile-avatar-fallback">{{ profileInitial }}</span>
                <span class="profile-avatar-edit">更换头像</span>
              </span>
            </el-upload>
            <div class="profile-summary-copy">
              <h2>{{ infoForm.nickname || "管理员" }}</h2>
              <p>{{ infoForm.intro || "还没有填写个人简介" }}</p>
            </div>
            <div class="profile-summary-meta">
              <div><span>账户状态</span><strong><i></i>正常</strong></div>
              <div><span>个人网站</span><strong>{{ infoForm.webSite || "暂未设置" }}</strong></div>
            </div>
          </aside>

          <section class="profile-form-panel">
            <div class="settings-panel-heading">
              <div>
                <h2>基本资料</h2>
                <p>这些信息会展示在你的个人页面和文章作者信息中。</p>
              </div>
              <span class="settings-panel-icon"><AppIcon name="settings" :size="18" /></span>
            </div>
            <el-form class="info-form" label-position="top" :model="infoForm">
              <el-form-item label="昵称">
                <el-input v-model="infoForm.nickname" placeholder="输入你的昵称" />
              </el-form-item>
              <el-form-item label="个人简介">
                <el-input v-model="infoForm.intro" type="textarea" :rows="5" resize="vertical" placeholder="简单介绍一下自己" />
              </el-form-item>
              <el-form-item label="个人网站">
                <el-input v-model="infoForm.webSite" placeholder="https://..." />
              </el-form-item>
              <div class="settings-form-actions">
                <el-button class="info-submit" @click="updateInfo" type="primary">保存资料</el-button>
              </div>
            </el-form>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <div class="password-layout">
          <div class="password-intro">
            <span class="password-icon"><AppIcon name="settings" :size="22" /></span>
            <h2>更新登录密码</h2>
            <p>定期更新密码，保护后台管理账户安全。</p>
          </div>
          <el-form class="password-form" label-position="top" :model="passwordForm">
            <el-form-item label="旧密码">
              <el-input @keyup.enter="updatePassword" v-model="passwordForm.oldPassword" show-password placeholder="输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input @keyup.enter="updatePassword" v-model="passwordForm.newPassword" show-password placeholder="至少 6 位字符" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input @keyup.enter="updatePassword" v-model="passwordForm.confirmPassword" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <div class="settings-form-actions">
              <el-button class="password-submit" type="primary" @click="updatePassword">更新密码</el-button>
            </div>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";
import { getCsrfHeaders } from "../../../../shared/http/csrf";
import { compressImageForUpload } from "../../utils/imageUpload";

export default {
  components: { AppIcon },
  data: function() {
    return {
      infoForm: {
        nickname: this.$store.state.nickname,
        intro: this.$store.state.intro,
        webSite: this.$store.state.webSite
      },
      passwordForm: {
        oldPassword: "",
        newPassword: "",
        confirmPassword: ""
      },
      activeName: "info"
    };
  },
  methods: {
    updateAvatar(response) {
      if (response.flag) {
        this.$message.success(response.message);
        this.$store.commit("updateAvatar", response.data);
      } else {
        this.$message.error(response.message);
      }
    },
    beforeUpload(file) {
      return compressImageForUpload(file, this.config.UPLOAD_SIZE);
    },
    uploadError() {
      this.$message.error("头像上传失败，请刷新页面后重试");
    },
    updateInfo() {
      if (this.infoForm.nickname.trim() == "") {
        this.$message.error("昵称不能为空");
        return false;
      }
      this.$api.auth.updateInfo(this.infoForm).then(data => {
        if (data.flag) {
          this.$message.success(data.message);
          this.$store.commit("updateUserInfo", this.infoForm);
        } else {
          this.$message.error(data.message);
        }
      });
    },
    updatePassword() {
      if (this.passwordForm.oldPassword.trim() == "") {
        this.$message.error("旧密码不能为空");
        return false;
      }
      if (this.passwordForm.newPassword.trim() == "") {
        this.$message.error("新密码不能为空");
        return false;
      }
      if (this.passwordForm.newPassword.length < 6) {
        this.$message.error("新密码不能少于6位");
        return false;
      }
      if (this.passwordForm.newPassword != this.passwordForm.confirmPassword) {
        this.$message.error("两次密码输入不一致");
        return false;
      }
      this.$api.admin
        .updateAdminPassword(this.passwordForm)
        .then(data => {
          if (data.flag) {
            this.passwordForm.oldPassword = "";
            this.passwordForm.newPassword = "";
            this.passwordForm.confirmPassword = "";
            this.$message.success(data.message);
          } else {
            this.$message.error(data.message);
          }
        });
    },
  },
  computed: {
    avatar() {
      return this.$store.state.avatar;
    },
    profileInitial() {
      return (this.infoForm.nickname || "管").slice(0, 1).toUpperCase();
    },
    uploadHeaders() {
      return getCsrfHeaders();
    }
  }
};
</script>

<style scoped>
.settings-card {
  min-height: auto !important;
}

.settings-card :deep(.el-card__body) {
  min-height: auto !important;
}

.avatar-container {
  text-align: center;
}
.el-icon-message-solid {
  color: #f56c6c;
  margin-right: 0.3rem;
}
.avatar-upload-content {
  position: relative;
  display: grid;
  width: 120px;
  height: 120px;
  place-items: center;
  overflow: hidden;
}

.avatar-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}
.avatar-uploader :deep(.el-upload:hover) {
  border-color: #409eff;
}
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  text-align: center;
}
.avatar {
  width: 120px;
  height: 120px;
  display: block;
  object-fit: cover;
}
.avatar-placeholder {
  display: grid;
  place-items: center;
  gap: 5px;
  color: #8c939d;
  font-size: 12px;
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
.info-container {
  display: flex;
  align-items: flex-start;
  gap: 3rem;
  margin-left: 0;
  margin-top: 2.25rem;
}

.info-form,
.password-form {
  width: 320px;
  max-width: 100%;
}

.info-submit,
.password-submit {
  margin-left: 4.375rem;
  box-shadow: none !important;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .info-container {
    align-items: stretch;
    flex-direction: column;
    gap: 1.25rem;
    margin-top: 1.25rem;
  }

  .info-container .avatar-uploader {
    align-self: center;
  }

  .info-form,
  .password-form {
    width: 100% !important;
    padding-bottom: 0.5rem;
  }

  .info-form :deep(.el-form-item),
  .password-form :deep(.el-form-item) {
    display: block;
    margin-bottom: 1rem;
  }

  .info-form :deep(.el-form-item__label),
  .password-form :deep(.el-form-item__label) {
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

  .info-form :deep(.el-form-item__content),
  .password-form :deep(.el-form-item__content) {
    width: 100%;
    min-width: 0;
    margin-left: 0 !important;
  }

  .info-submit,
  .password-submit {
    width: 100%;
    display: flex;
    margin: 1.25rem 0 0;
  }
}

/* Redesigned account center */
.settings-card {
  min-height: auto !important;
}

.settings-card :deep(.el-card__body) {
  min-height: auto !important;
  padding: clamp(22px, 4vw, 34px);
}

.settings-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.settings-header .title {
  margin-bottom: 8px;
}

.settings-description {
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.settings-header-mark {
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

.settings-header-mark span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--admin-green);
  box-shadow: 0 0 0 3px rgba(52, 199, 89, 0.12);
}

.settings-tabs {
  margin-top: 24px;
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 26px;
}

.settings-tabs :deep(.el-tabs__item) {
  padding: 0 4px;
  margin-right: 26px;
  font-size: 14px;
}

.profile-layout {
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}

.profile-summary,
.profile-form-panel,
.password-layout {
  overflow: hidden;
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(29, 29, 31, 0.035);
}

.profile-summary {
  padding-bottom: 20px;
  text-align: center;
}

.profile-summary-top {
  height: 78px;
  background: linear-gradient(135deg, #dceeff 0%, #f5f9ff 100%);
}

.profile-avatar-uploader {
  display: block;
  width: max-content;
  margin: -44px auto 0;
}

.profile-avatar-frame {
  position: relative;
  display: grid;
  width: 88px;
  height: 88px;
  place-items: center;
  overflow: hidden;
  color: var(--admin-blue);
  background: var(--admin-blue-soft);
  border: 4px solid #fff;
  border-radius: 25px;
  box-shadow: 0 8px 20px rgba(29, 29, 31, 0.12);
  cursor: pointer;
}

.profile-avatar {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar-fallback {
  font-size: 28px;
  font-weight: 750;
}

.profile-avatar-edit {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 5px 2px;
  color: #fff;
  font-size: 10px;
  background: rgba(29, 29, 31, 0.68);
  opacity: 0;
  transition: opacity 180ms ease;
}

.profile-avatar-frame:hover .profile-avatar-edit,
.profile-avatar-frame:focus-within .profile-avatar-edit {
  opacity: 1;
}

.profile-summary-copy {
  padding: 14px 20px 0;
}

.profile-summary-copy h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 720;
}

.profile-summary-copy p {
  min-height: 42px;
  margin: 7px 0 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.profile-summary-meta {
  padding: 15px 20px 0;
  margin-top: 17px;
  border-top: 1px solid #eef0f4;
  text-align: left;
}

.profile-summary-meta div {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
}

.profile-summary-meta span {
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.profile-summary-meta strong {
  max-width: 150px;
  overflow: hidden;
  color: var(--admin-text-secondary);
  font-size: 11px;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-summary-meta strong i {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin: 0 5px 1px 0;
  background: var(--admin-green);
  border-radius: 50%;
}

.profile-form-panel {
  padding: 24px;
}

.settings-panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid #eef0f4;
}

.settings-panel-heading h2,
.password-intro h2 {
  margin: 0 0 5px;
  color: var(--admin-text);
  font-size: 16px;
  font-weight: 720;
}

.settings-panel-heading p,
.password-intro p {
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.settings-panel-icon,
.password-icon {
  display: inline-grid;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  color: var(--admin-blue);
  place-items: center;
  background: var(--admin-blue-soft);
  border-radius: 11px;
}

.info-form,
.password-form {
  width: 100%;
  max-width: none;
}

.info-form :deep(.el-form-item),
.password-form :deep(.el-form-item) {
  margin-bottom: 17px;
}

.info-form :deep(.el-form-item__label),
.password-form :deep(.el-form-item__label) {
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

.info-form :deep(.el-input__wrapper),
.info-form :deep(.el-textarea__inner),
.password-form :deep(.el-input__wrapper) {
  min-height: 44px;
  background: #fbfcff;
  border-color: #dfe5ee;
  border-radius: 11px;
}

.info-form :deep(.el-textarea__inner) {
  padding: 11px 13px;
}

.settings-form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 3px;
}

.info-submit,
.password-submit {
  min-width: 112px;
  margin: 0 !important;
  box-shadow: 0 6px 15px rgba(0, 113, 227, 0.16) !important;
}

.password-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
  padding: 24px;
}

.password-intro {
  padding: 4px 8px 0 2px;
}

.password-intro .password-icon {
  margin-bottom: 18px;
}

.password-intro h2 {
  font-size: 18px;
}

.password-intro p {
  line-height: 1.7;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .settings-header-mark {
    display: none;
  }

  .profile-layout,
  .password-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .profile-form-panel,
  .password-layout {
    padding: 18px;
  }

  .settings-form-actions {
    justify-content: stretch;
  }

  .info-submit,
  .password-submit {
    width: 100%;
  }
}
</style>
