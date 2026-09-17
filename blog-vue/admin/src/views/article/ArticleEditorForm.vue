<template>
  <el-dialog
    :model-value="modelValue"
    width="40%"
    top="3vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header><div class="dialog-title-container">发布文章</div></template>
    <el-form label-width="80px" size="medium" :model="localArticle">
      <el-form-item label="文章类型">
        <el-select v-model="localArticle.type" placeholder="请选择类型">
          <el-option
            v-for="item in typeList"
            :key="item.type"
            :label="item.desc"
            :value="item.type"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="localArticle.type !== 1" label="原文地址">
        <el-input v-model="localArticle.originalUrl" placeholder="请填写原文链接" />
      </el-form-item>

      <el-form-item label="上传封面">
        <el-upload
          class="upload-cover"
          drag
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="uploadRequest"
        >
          <i v-if="!localArticle.articleCover" class="el-icon-upload" />
          <div v-if="!localArticle.articleCover" class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <img
            v-else
            :src="localArticle.articleCover"
            width="360"
            height="180"
            alt="文章封面"
          />
        </el-upload>
      </el-form-item>

      <el-form-item label="置顶">
        <el-switch
          v-model="localArticle.isTop"
          active-color="#13ce66"
          inactive-color="#F4F4F5"
          :active-value="1"
          :inactive-value="0"
        />
      </el-form-item>

      <el-form-item label="发布形式">
        <el-radio-group v-model="localArticle.status">
          <el-radio :value="1">公开</el-radio>
          <el-radio :value="2">私密</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <div>
        <el-button @click="$emit('update:modelValue', false)">取 消</el-button>
        <el-button type="danger" @click="$emit('save')">发 表</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
const cloneArticle = article => ({ ...article });

export default {
  name: "ArticleEditorForm",
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
.dialog-title-container {
  font-weight: 600;
}

.upload-cover {
  width: 360px;
}
</style>
