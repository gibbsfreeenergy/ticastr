<template>
  <el-dialog
    class="publish-dialog"
    :model-value="modelValue"
    width="min(680px, calc(100vw - 32px))"
    top="8vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="publish-dialog-heading">
        <div>
          <div class="dialog-title-container">发布文章</div>
          <p>完善文章属性，确认后会立即出现在文章列表中。</p>
        </div>
      </div>
    </template>
    <el-form class="publish-form" label-position="top" size="medium" :model="localArticle">
      <div class="publish-form-grid">
        <el-form-item label="文章类型" class="publish-field">
          <el-select v-model="localArticle.type" placeholder="请选择类型">
            <el-option
              v-for="item in typeList"
              :key="item.type"
              :label="item.desc"
              :value="item.type"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="localArticle.type !== 1" label="原文地址" class="publish-field">
          <el-input v-model="localArticle.originalUrl" placeholder="请填写原文链接" />
        </el-form-item>

        <el-form-item label="文章封面" class="publish-field publish-field-wide">
          <el-upload
            class="upload-cover"
            drag
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="uploadRequest"
          >
            <div v-if="!localArticle.articleCover" class="upload-cover-empty">
              <span class="upload-cover-icon"><AppIcon name="plus" :size="22" /></span>
              <strong>拖拽图片到这里，或点击上传</strong>
              <small>建议使用 16:9 比例的图片，展示效果更佳</small>
            </div>
            <img
              v-else
              class="cover-preview"
              :src="localArticle.articleCover"
              alt="文章封面"
            />
          </el-upload>
        </el-form-item>
      </div>

      <div class="publish-option-row">
        <el-form-item label="置顶文章">
          <el-switch
            v-model="localArticle.isTop"
            :active-value="1"
            :inactive-value="0"
          />
          <span class="option-help">在列表中优先展示</span>
        </el-form-item>

        <el-form-item label="可见范围">
          <el-radio-group v-model="localArticle.status" class="publish-radio-group">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="2">私密</el-radio>
          </el-radio-group>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <div class="publish-dialog-footer">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" @click="$emit('save')">确认发布</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";

const cloneArticle = article => ({ ...article });

export default {
  name: "ArticleEditorForm",
  components: { AppIcon },
  emits: ["update:modelValue", "save", "update-article"],
  props: {
    modelValue: { type: Boolean, default: false },
    article: { type: Object, required: true },
    typeList: { type: Array, default: () => [] },
    beforeUpload: { type: Function, required: true },
    uploadRequest: { type: Function, required: true }
  },
  data() {
    const localArticle = cloneArticle(this.article);
    return {
      localArticle,
      localArticleSerialization: JSON.stringify(localArticle)
    };
  },
  watch: {
    article: {
      deep: true,
      handler(value) {
        const next = cloneArticle(value);
        const serialization = JSON.stringify(next);
        if (serialization !== this.localArticleSerialization) {
          this.localArticle = next;
          this.localArticleSerialization = serialization;
        }
      }
    },
    localArticle: {
      deep: true,
      handler() {
        const next = cloneArticle(this.localArticle);
        const serialization = JSON.stringify(next);
        if (serialization === this.localArticleSerialization) return;
        this.localArticleSerialization = serialization;
        this.$emit("update-article", next);
      }
    }
  }
};
</script>

<style scoped>
.publish-dialog-heading p {
  margin: 7px 0 0;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.publish-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.publish-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.publish-field-wide {
  grid-column: 1 / -1;
}

.publish-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.publish-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
}

.publish-form :deep(.el-input),
.publish-form :deep(.el-select) {
  width: 100%;
}

.publish-form :deep(.el-input__wrapper),
.publish-form :deep(.el-select__wrapper) {
  min-height: 44px;
  background: #fbfcff;
  border-color: #dfe5ee;
  border-radius: 11px;
}

.publish-form :deep(.el-input__wrapper:hover),
.publish-form :deep(.el-select__wrapper:hover) {
  border-color: #a9c9ee;
}

.upload-cover {
  width: 100%;
}

.upload-cover :deep(.el-upload),
.upload-cover :deep(.el-upload-dragger) {
  width: 100%;
}

.upload-cover :deep(.el-upload-dragger) {
  display: grid;
  min-height: 172px;
  padding: 18px;
  place-items: center;
  color: var(--admin-text-secondary);
  background: #f8fbff;
  border: 1px dashed #b9d3f2;
  border-radius: 13px;
  transition: background 180ms ease, border-color 180ms ease;
}

.upload-cover :deep(.el-upload-dragger:hover) {
  background: #f2f8ff;
  border-color: var(--admin-blue);
}

.upload-cover-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 7px;
  text-align: center;
}

.upload-cover-icon {
  display: inline-grid;
  width: 42px;
  height: 42px;
  color: var(--admin-blue);
  place-items: center;
  background: var(--admin-blue-soft);
  border-radius: 12px;
}

.upload-cover-empty strong {
  color: var(--admin-text);
  font-size: 13px;
  font-weight: 650;
}

.upload-cover-empty small {
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.cover-preview {
  display: block;
  width: 100%;
  max-height: 230px;
  object-fit: cover;
}

.publish-option-row {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.publish-option-row :deep(.el-form-item) {
  margin-bottom: 0;
}

.option-help {
  margin-left: 10px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.publish-radio-group {
  display: flex;
  align-items: center;
  min-height: 44px;
  gap: 18px;
}

.publish-radio-group :deep(.el-radio) {
  margin-right: 0;
}

.publish-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.publish-dialog-footer .el-button {
  min-width: 96px;
}

:deep(.publish-dialog .el-dialog__header) {
  padding: 24px 28px 19px;
}

:deep(.publish-dialog .el-dialog__body) {
  padding: 23px 28px 25px;
}

:deep(.publish-dialog .el-dialog__footer) {
  padding: 16px 28px 22px;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .publish-form-grid,
  .publish-option-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .publish-dialog-footer .el-button {
    flex: 1;
  }

  :deep(.publish-dialog .el-dialog__header),
  :deep(.publish-dialog .el-dialog__body),
  :deep(.publish-dialog .el-dialog__footer) {
    padding-right: 20px;
    padding-left: 20px;
  }
}
</style>
