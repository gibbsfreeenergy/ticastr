<template>
  <el-card class="main-card settings-card">
    <el-tabs v-model="activeName">
      <!-- 修改信息 -->
      <el-tab-pane label="修改信息" name="info">
        <div class="info-container">
          <el-upload
            class="avatar-uploader"
            aria-label="更换头像"
            :action="$api.auth.avatarUploadUrl"
            :headers="uploadHeaders"
            :with-credentials="true"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-success="updateAvatar"
            :on-error="uploadError"
          >
            <span class="avatar-upload-content">
              <img v-if="avatar" :src="avatar" class="avatar" alt="当前头像" />
              <span v-if="avatar" class="avatar-edit-hint">更换头像</span>
              <span v-else class="avatar-placeholder">
                <i class="el-icon-plus avatar-uploader-icon" />
                <span>上传头像</span>
              </span>
            </span>
          </el-upload>
          <el-form
            class="info-form"
            label-width="70px"
            :model="infoForm"
          >
            <el-form-item label="昵称">
              <el-input v-model="infoForm.nickname" size="small" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="infoForm.intro" type="textarea" :rows="3" resize="vertical" size="small" />
            </el-form-item>
            <el-form-item label="个人网站">
              <el-input v-model="infoForm.webSite" size="small" />
            </el-form-item>
            <el-button
              class="info-submit"
              @click="updateInfo"
              type="primary"
              size="medium"
            >
              保存资料
            </el-button>
          </el-form>
        </div>
      </el-tab-pane>
      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form class="password-form" label-width="70px" :model="passwordForm">
          <el-form-item label="旧密码">
            <el-input
              @keyup.enter="updatePassword"
              v-model="passwordForm.oldPassword"
              size="small"
              show-password
            />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input
              @keyup.enter="updatePassword"
              v-model="passwordForm.newPassword"
              size="small"
              show-password
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              @keyup.enter="updatePassword"
              v-model="passwordForm.confirmPassword"
              size="small"
              show-password
            />
          </el-form-item>
          <el-button
            class="password-submit"
            type="primary"
            size="medium"
            @click="updatePassword"
          >
            更新密码
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script>
import { getCsrfHeaders } from "../../../../shared/http/csrf";
import { compressImageForUpload } from "../../utils/imageUpload";

export default {
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
</style>
