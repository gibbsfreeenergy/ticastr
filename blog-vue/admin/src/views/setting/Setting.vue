<template>
  <el-card class="main-card">
    <el-tabs v-model="activeName">
      <!-- 修改信息 -->
      <el-tab-pane label="修改信息" name="info">
        <div class="info-container">
          <el-upload
            class="avatar-uploader"
            :action="$api.auth.avatarUploadUrl"
            :show-file-list="false"
            :on-success="updateAvatar"
          >
            <img v-if="avatar" :src="avatar" class="avatar" />
            <i v-else class="el-icon-plus avatar-uploader-icon" />
          </el-upload>
          <el-form
            label-width="70px"
            :model="infoForm"
            style="width:320px;margin-left:3rem"
          >
            <el-form-item label="昵称">
              <el-input v-model="infoForm.nickname" size="small" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="infoForm.intro" size="small" />
            </el-form-item>
            <el-form-item label="个人网站">
              <el-input v-model="infoForm.webSite" size="small" />
            </el-form-item>
            <el-button
              @click="updateInfo"
              type="primary"
              size="medium"
              style="margin-left:4.375rem"
            >
              修改
            </el-button>
          </el-form>
        </div>
      </el-tab-pane>
      <!-- 修改密码 -->
      <el-tab-pane label="修改密码" name="password">
        <el-form label-width="70px" :model="passwordForm" style="width:320px">
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
            type="primary"
            size="medium"
            style="margin-left:4.4rem"
            @click="updatePassword"
          >
            修改
          </el-button>
        </el-form>
      </el-tab-pane>
      <!-- 基础设施配置 -->
      <el-tab-pane label="基础设施" name="infrastructure">
        <div class="infrastructure-container">
          <div class="infrastructure-header">
            <div>
              <h3>对象存储</h3>
              <p>系统同一时间只使用一个 provider；切换前会执行写入、读取和删除验证。</p>
            </div>
            <el-button size="small" @click="loadStorageProviders" :loading="storageLoading">
              刷新状态
            </el-button>
          </div>
          <el-radio-group v-model="selectedProvider" class="provider-group">
            <el-radio-button
              v-for="item of storageProviders"
              :key="item.provider"
              :label="item.provider"
              :disabled="!item.configured"
            >
              {{ item.provider.toUpperCase() }}
              <span v-if="item.active">（当前）</span>
            </el-radio-button>
          </el-radio-group>
          <div class="provider-actions">
            <el-button
              type="primary"
              :loading="switchingProvider"
              :disabled="!selectedProvider || selectedProvider === currentProvider"
              @click="switchStorageProvider"
            >
              验证并切换
            </el-button>
            <el-button
              v-if="selectedProvider"
              :loading="validatingProvider === selectedProvider"
              @click="validateStorageProvider(selectedProvider)"
            >
              运行验证
            </el-button>
          </div>
          <el-table :data="storageProviders" size="small" class="provider-table">
            <el-table-column prop="provider" label="Provider" width="120" />
            <el-table-column label="配置状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.configured ? 'success' : 'info'">
                  {{ row.configured ? "已配置" : "未配置" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="凭据" width="120">
              <template #default="{ row }">
                {{ row.credentialsConfigured ? "已设置" : "无需/未设置" }}
              </template>
            </el-table-column>
            <el-table-column label="最近验证" min-width="180">
              <template #default="{ row }">
                <el-tag v-if="validationResults[row.provider]" :type="validationResults[row.provider].success ? 'success' : 'danger'">
                  {{ validationResults[row.provider].message }}
                </el-tag>
                <span v-else class="muted">尚未验证</span>
              </template>
            </el-table-column>
          </el-table>
          <p class="infrastructure-note">
            凭据和 endpoint 只从后端环境变量读取，不会回传到浏览器或写入数据库。
          </p>
        </div>
      </el-tab-pane>
      <el-tab-pane label="可靠事件" name="outbox">
        <OutboxPanel />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script>
import OutboxPanel from "./OutboxPanel.vue";

export default {
  components: { OutboxPanel },
  created() {
    this.loadStorageProviders();
  },
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
      activeName: "info",
      storageProviders: [],
      currentProvider: "",
      selectedProvider: "",
      storageLoading: false,
      switchingProvider: false,
      validatingProvider: "",
      validationResults: {}
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
    async loadStorageProviders() {
      this.storageLoading = true;
      try {
        const [currentResponse, providersResponse] = await Promise.all([
          this.$api.admin.storageProvider(),
          this.$api.admin.storageProviders()
        ]);
        this.currentProvider = currentResponse.data?.activeProvider || "";
        this.selectedProvider = this.currentProvider;
        this.storageProviders = providersResponse.data || [];
      } finally {
        this.storageLoading = false;
      }
    },
    async validateStorageProvider(provider) {
      this.validatingProvider = provider;
      try {
        const response = await this.$api.admin.validateStorageProvider(provider);
        this.validationResults = { ...this.validationResults, [provider]: response.data };
        if (response.data?.success) this.$message.success(response.data.message);
        else this.$message.warning(response.data?.message || "Provider 验证失败");
      } finally {
        this.validatingProvider = "";
      }
    },
    async switchStorageProvider() {
      if (!this.selectedProvider || this.selectedProvider === this.currentProvider) return;
      this.switchingProvider = true;
      try {
        const validation = await this.$api.admin.validateStorageProvider(this.selectedProvider);
        this.validationResults = { ...this.validationResults, [this.selectedProvider]: validation.data };
        if (!validation.data?.success) {
          this.$message.error(validation.data?.message || "Provider 验证失败");
          return;
        }
        const response = await this.$api.admin.switchStorageProvider({ provider: this.selectedProvider });
        this.currentProvider = response.data?.activeProvider || this.selectedProvider;
        this.$message.success("对象存储切换成功");
        await this.loadStorageProviders();
      } finally {
        this.switchingProvider = false;
      }
    },
  },
  computed: {
    avatar() {
      return this.$store.state.avatar;
    }
  }
};
</script>

<style scoped>
.avatar-container {
  text-align: center;
}
.el-icon-message-solid {
  color: #f56c6c;
  margin-right: 0.3rem;
}
.avatar-uploader .el-upload {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}
.avatar-uploader .el-upload:hover {
  border-color: #409eff;
}
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 120px;
  height: 120px;
  line-height: 120px;
  text-align: center;
}
.avatar {
  width: 120px;
  height: 120px;
  display: block;
}
.info-container {
  display: flex;
  align-items: center;
  margin-left: 20%;
  margin-top: 5rem;
}
.infrastructure-container {
  max-width: 900px;
  margin: 2rem auto;
}
.infrastructure-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.infrastructure-header h3 {
  margin: 0 0 0.5rem;
}
.infrastructure-header p,
.infrastructure-note {
  color: #7c8494;
  font-size: 0.875rem;
}
.provider-group {
  margin: 1.5rem 0 1rem;
}
.provider-actions {
  margin-bottom: 1rem;
}
.provider-table {
  width: 100%;
}
.muted {
  color: #9aa1ad;
}
</style>
