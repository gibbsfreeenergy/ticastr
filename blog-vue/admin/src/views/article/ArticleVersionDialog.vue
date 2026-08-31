<template>
  <el-dialog
    :model-value="modelValue"
    title="文章内容历史"
    width="70%"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-table :data="versions" size="small" row-key="assetId">
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="sizeBytes" label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column prop="checksum" label="校验摘要" min-width="160">
        <template #default="{ row }">{{ shortChecksum(row.checksum) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.version === currentVersion || row.status === 'DELETED'"
            @click="$emit('restore', row)"
          >
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="version-footer">
      <span v-if="hasNext">还有更早版本</span>
      <el-button v-if="hasNext" size="small" :loading="loading" @click="$emit('next')">
        加载更多
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: "ArticleVersionDialog",
  emits: ["update:modelValue", "restore", "next"],
  props: {
    modelValue: { type: Boolean, default: false },
    versions: { type: Array, default: () => [] },
    currentVersion: { type: [Number, String], default: null },
    hasNext: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  },
  methods: {
    shortChecksum(value) {
      const checksum = String(value || "");
      return checksum.length > 16 ? checksum.slice(0, 16) + "…" : checksum || "—";
    },
    formatSize(value) {
      const size = Number(value || 0);
      if (size < 1024) return size + " B";
      return (size / 1024).toFixed(1) + " KB";
    }
  }
};
</script>

<style scoped>
.version-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
  padding-top: 1rem;
  color: #7c8494;
  font-size: 0.8125rem;
}
</style>
