<template>
  <section class="storage-config-panel" aria-labelledby="storage-config-title">
    <div class="storage-config-header">
      <div>
        <h3 id="storage-config-title">对象存储配置</h3>
        <p>可维护多个存储档案；启用操作只影响后续上传，历史资源仍使用创建时的档案。</p>
      </div>
      <div class="storage-config-header-actions">
        <el-button data-test="add-storage-config" type="primary" size="small" @click="openCreate">新增配置</el-button>
        <el-button size="small" :loading="loading" @click="load">刷新状态</el-button>
      </div>
    </div>

    <p v-if="error" class="storage-config-error" role="alert">{{ error }}</p>
    <p v-if="loading && !configs.length" class="storage-config-muted" aria-live="polite">正在读取存储配置…</p>

    <div v-if="configs.length" class="storage-config-grid" aria-live="polite">
      <article v-for="config in configs" :key="config.id" class="storage-config-card">
        <div class="storage-config-card-header">
          <div>
            <strong>{{ config.name }}</strong>
            <span class="storage-config-provider">{{ config.provider.toUpperCase() }}</span>
          </div>
          <el-tag :type="config.active ? 'success' : 'info'">{{ config.active ? "当前启用" : "未启用" }}</el-tag>
        </div>

        <dl class="storage-config-details">
          <div><dt>位置</dt><dd>{{ location(config) }}</dd></div>
          <div v-if="config.provider !== 'local'"><dt>Region</dt><dd>{{ config.region || "—" }}</dd></div>
          <div><dt>公共地址</dt><dd>{{ config.publicUrl || "—" }}</dd></div>
          <div><dt>凭据</dt><dd>{{ config.credentialsConfigured ? "已设置" : "无需/未设置" }}</dd></div>
          <div><dt>验证</dt><dd>{{ validationText(config.validation) }}</dd></div>
          <div><dt>使用量</dt><dd>{{ usageText(config.usage) }}</dd></div>
          <div v-if="config.usage.latestModified"><dt>最近对象</dt><dd>{{ formatDate(config.usage.latestModified) }}</dd></div>
          <div v-if="config.usage.checkedAt"><dt>统计时间</dt><dd>{{ formatDate(config.usage.checkedAt) }}</dd></div>
        </dl>
        <p v-if="config.validation.message" class="storage-config-status-detail">{{ config.validation.message }}</p>
        <p v-if="config.usage.status === 'FAILED'" class="storage-config-failure">
          使用量刷新失败；已保留上次成功统计。<span v-if="config.usage.error">{{ config.usage.error }}</span>
        </p>

        <div class="storage-config-actions" aria-label="配置操作">
          <el-button :data-test="`edit-storage-config-${config.id}`" link type="primary" @click="openEdit(config)">编辑</el-button>
          <el-button
            :data-test="`delete-storage-config-${config.id}`"
            link
            type="danger"
            :disabled="config.active"
            @click="removeConfig(config)"
          >删除</el-button>
          <el-button
            :data-test="`validate-storage-config-${config.id}`"
            link
            :loading="validatingId === config.id"
            @click="validateConfig(config)"
          >验证</el-button>
          <el-button
            :data-test="`activate-storage-config-${config.id}`"
            link
            type="success"
            :disabled="config.active"
            :loading="activatingId === config.id"
            @click="activateConfig(config)"
          >启用</el-button>
          <el-button
            :data-test="`refresh-storage-usage-${config.id}`"
            link
            :loading="usageRefreshingId === config.id"
            @click="refreshUsage(config)"
          >刷新用量</el-button>
        </div>
      </article>
    </div>
    <p v-else-if="!loading && !error" class="storage-config-muted">暂无存储配置。</p>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑存储配置' : '新增存储配置'" width="560px">
      <el-form label-width="105px" :model="form">
        <el-form-item label="配置名称">
          <el-input v-model="form.name" aria-label="配置名称" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="form.provider" data-test="provider" aria-label="存储来源" style="width:100%">
            <el-option label="本地存储" value="local" />
            <el-option label="腾讯云 COS" value="cos" />
            <el-option label="阿里云 OSS" value="oss" />
            <el-option label="火山引擎 TOS" value="tos" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.provider === 'local'" label="本地根目录">
          <el-input v-model="form.localRoot" data-test="local-root" aria-label="本地根目录" />
        </el-form-item>
        <template v-else>
          <el-form-item label="Endpoint">
            <el-input v-model="form.endpoint" data-test="endpoint" aria-label="Endpoint" />
          </el-form-item>
          <el-form-item label="Region">
            <el-input v-model="form.region" data-test="region" aria-label="Region" />
          </el-form-item>
          <el-form-item label="Bucket">
            <el-input v-model="form.bucket" data-test="bucket" aria-label="Bucket" />
          </el-form-item>
          <el-form-item label="Access Key ID">
            <el-input v-model="form.accessKeyId" data-test="access-key-id" aria-label="Access Key ID" autocomplete="off" />
          </el-form-item>
          <el-form-item label="Access Key Secret">
            <el-input v-model="form.accessKeySecret" data-test="access-key-secret" aria-label="Access Key Secret" autocomplete="new-password" show-password />
          </el-form-item>
        </template>
        <el-form-item label="公共地址">
          <el-input v-model="form.publicUrl" aria-label="公共地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button data-test="save-storage-config" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script>
const providers = ["local", "cos", "oss", "tos"];
const statuses = ["NEVER", "SUCCESS", "FAILED"];
const validationMessages = new Set(["验证成功", "配置验证失败", "配置字段不完整"]);
const usageErrors = new Set(["使用量刷新失败"]);

function text(value) {
  return typeof value === "string" ? value : "";
}

function date(value) {
  return typeof value === "string" && value ? value : "";
}

function number(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

function status(value) {
  return statuses.includes(value) ? value : "NEVER";
}

function statusDetail(value, allowedMessages) {
  if (typeof value !== "string") return "";
  const detail = value.trim();
  return allowedMessages.has(detail) ? detail : "";
}

function safeValidation(value) {
  const valueStatus = status(value?.status);
  return {
    status: valueStatus,
    success: valueStatus === "SUCCESS",
    validatedAt: date(value?.validatedAt),
    message: statusDetail(value?.message, validationMessages)
  };
}

function safeUsage(value) {
  const valueStatus = status(value?.status);
  return {
    status: valueStatus,
    objectCount: number(value?.objectCount),
    bytes: number(value?.bytes),
    latestModified: date(value?.latestModified),
    checkedAt: date(value?.checkedAt),
    error: statusDetail(value?.error, usageErrors)
  };
}

function safeConfig(value) {
  return {
    id: value?.id,
    name: text(value?.name),
    provider: providers.includes(value?.provider) ? value.provider : "local",
    active: value?.active === true,
    configured: value?.configured === true,
    credentialsConfigured: value?.credentialsConfigured === true,
    endpoint: text(value?.endpoint),
    region: text(value?.region),
    bucket: text(value?.bucket),
    localRoot: text(value?.localRoot),
    publicUrl: text(value?.publicUrl),
    validation: safeValidation(value?.validation),
    usage: safeUsage(value?.usage)
  };
}

function emptyForm() {
  return {
    name: "",
    provider: "local",
    endpoint: "",
    region: "",
    bucket: "",
    localRoot: "",
    publicUrl: "",
    accessKeyId: "",
    accessKeySecret: ""
  };
}

export default {
  name: "StorageConfigPanel",
  data() {
    return {
      configs: [],
      activeConfigId: null,
      dialogVisible: false,
      editingId: null,
      form: emptyForm(),
      loading: false,
      saving: false,
      validatingId: null,
      activatingId: null,
      usageRefreshingId: null,
      error: ""
    };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = "";
      try {
        const response = await this.$api.admin.storageConfigs();
        const data = response?.data || {};
        this.activeConfigId = typeof data.activeConfigId === "number" ? data.activeConfigId : null;
        this.configs = Array.isArray(data.configs) ? data.configs.map(safeConfig) : [];
      } catch {
        this.error = "存储配置暂时无法读取，请稍后重试";
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.editingId = null;
      this.form = emptyForm();
      this.dialogVisible = true;
    },
    openEdit(config) {
      this.editingId = config.id;
      this.form = {
        name: text(config.name),
        provider: providers.includes(config.provider) ? config.provider : "local",
        endpoint: text(config.endpoint),
        region: text(config.region),
        bucket: text(config.bucket),
        localRoot: text(config.localRoot),
        publicUrl: text(config.publicUrl),
        accessKeyId: "",
        accessKeySecret: ""
      };
      this.dialogVisible = true;
    },
    requestPayload() {
      return {
        name: this.form.name,
        provider: this.form.provider,
        endpoint: this.form.endpoint,
        region: this.form.region,
        bucket: this.form.bucket,
        localRoot: this.form.localRoot,
        publicUrl: this.form.publicUrl,
        accessKeyId: this.form.accessKeyId,
        accessKeySecret: this.form.accessKeySecret
      };
    },
    async save() {
      this.saving = true;
      this.error = "";
      try {
        const payload = this.requestPayload();
        if (this.editingId === null) await this.$api.admin.createStorageConfig(payload);
        else await this.$api.admin.updateStorageConfig(this.editingId, payload);
        this.$message.success(this.editingId === null ? "存储配置已新增" : "存储配置已更新");
        this.dialogVisible = false;
        await this.load();
      } catch {
        this.error = "存储配置保存失败，请检查字段后重试";
      } finally {
        this.saving = false;
      }
    },
    async removeConfig(config) {
      if (config.active || !window.confirm(`确认删除“${config.name}”吗？`)) return;
      this.error = "";
      try {
        await this.$api.admin.deleteStorageConfig(config.id);
        this.$message.success("存储配置已删除");
        await this.load();
      } catch {
        this.error = "存储配置删除失败，请稍后重试";
      }
    },
    async validateConfig(config) {
      this.validatingId = config.id;
      this.error = "";
      try {
        const response = await this.$api.admin.validateStorageConfig(config.id);
        const validation = safeValidation(response?.data);
        this.updateConfig(config.id, { validation });
        if (validation.success) this.$message.success("存储配置验证成功");
        else this.$message.warning("存储配置验证失败");
        await this.load();
      } catch {
        this.error = "存储配置验证失败，请稍后重试";
      } finally {
        this.validatingId = null;
      }
    },
    async activateConfig(config) {
      this.activatingId = config.id;
      this.error = "";
      try {
        await this.$api.admin.activateStorageConfig(config.id);
        this.$message.success("对象存储已启用");
        await this.load();
      } catch {
        this.error = "存储配置启用失败，请先验证配置";
      } finally {
        this.activatingId = null;
      }
    },
    async refreshUsage(config) {
      this.usageRefreshingId = config.id;
      this.error = "";
      try {
        const response = await this.$api.admin.refreshStorageUsage(config.id);
        this.updateConfig(config.id, { usage: safeUsage(response?.data) });
        await this.load();
      } catch {
        this.error = "使用量刷新失败，请稍后重试";
      } finally {
        this.usageRefreshingId = null;
      }
    },
    updateConfig(id, patch) {
      this.configs = this.configs.map(config => config.id === id ? { ...config, ...patch } : config);
    },
    location(config) {
      return config.provider === "local" ? config.localRoot || "—" : config.bucket || "—";
    },
    validationText(validation) {
      return validation.status === "SUCCESS" ? "验证成功" : validation.status === "FAILED" ? "验证失败" : "尚未验证";
    },
    usageText(usage) {
      const count = usage.objectCount === null ? "暂无统计" : `${usage.objectCount} 个对象`;
      const bytes = usage.bytes === null ? "" : `，${this.formatBytes(usage.bytes)}`;
      return `${count}${bytes}`;
    },
    formatBytes(bytes) {
      if (bytes === 0) return "0 B";
      const units = ["B", "KB", "MB", "GB", "TB"];
      const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
      const value = bytes / Math.pow(1024, index);
      return `${Number(value.toFixed(value >= 10 || index === 0 ? 0 : 1))} ${units[index]}`;
    },
    formatDate(value) {
      return value ? value.replace("T", " ").replace(/Z$/, "") : "—";
    }
  }
};
</script>

<style scoped>
.storage-config-panel { max-width: 980px; margin: 1.5rem auto; }
.storage-config-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.storage-config-header h3 { margin: 0 0 0.5rem; }
.storage-config-header p, .storage-config-muted { color: #7c8494; font-size: 0.875rem; }
.storage-config-header-actions, .storage-config-actions { display: flex; flex-wrap: wrap; gap: 0.35rem; }
.storage-config-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1rem; margin-top: 1rem; }
.storage-config-card { border: 1px solid #ebeef5; border-radius: 4px; padding: 1rem; background: #fff; }
.storage-config-card-header { display: flex; justify-content: space-between; gap: 0.75rem; align-items: flex-start; }
.storage-config-provider { margin-left: 0.5rem; color: #7c8494; font-size: 0.8125rem; }
.storage-config-details { margin: 1rem 0; }
.storage-config-details div { display: grid; grid-template-columns: 4.75rem minmax(0, 1fr); gap: 0.5rem; margin-top: 0.45rem; font-size: 0.875rem; }
.storage-config-details dt { color: #7c8494; }
.storage-config-details dd { margin: 0; overflow-wrap: anywhere; }
.storage-config-status-detail { color: #7c8494; font-size: 0.8125rem; }
.storage-config-failure, .storage-config-error { color: #f56c6c; font-size: 0.8125rem; }
.storage-config-actions { border-top: 1px solid #f2f5fa; padding-top: 0.75rem; }

@media (max-width: 600px) {
  .storage-config-header { flex-direction: column; }
  .storage-config-header-actions { width: 100%; }
  .storage-config-grid { grid-template-columns: 1fr; }
}
</style>
