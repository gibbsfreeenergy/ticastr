<template>
  <el-dialog
    :model-value="modelValue"
    width="40%"
    top="3vh"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header><div class="dialog-title-container">发布文章</div></template>
    <el-form label-width="80px" size="medium" :model="localArticle">
      <el-form-item label="文章分类">
        <el-tag
          v-if="localArticle.categoryName"
          type="success"
          closable
          style="margin:0 1rem 0 0"
          @close="$emit('remove-category')"
        >
          {{ localArticle.categoryName }}
        </el-tag>
        <el-popover v-else placement="bottom-start" width="460" trigger="click">
          <div class="popover-title">分类</div>
          <el-autocomplete
            v-model="localCategoryName"
            style="width:100%"
            :fetch-suggestions="searchCategories"
            placeholder="请输入分类名搜索，enter 可添加自定义分类"
            :trigger-on-focus="false"
            @keyup.enter="saveCategory"
            @select="handleSelectCategory"
          >
            <template #default="{ item }"><div>{{ item.categoryName }}</div></template>
          </el-autocomplete>
          <div class="popover-container">
            <button
              v-for="item of categoryList"
              :key="item.id || item.categoryName"
              type="button"
              class="category-item"
              @click="$emit('add-category', item)"
            >
              {{ item.categoryName }}
            </button>
          </div>
          <template #reference><el-button type="success" plain size="small">添加分类</el-button></template>
        </el-popover>
      </el-form-item>

      <el-form-item label="文章标签">
        <el-tag
          v-for="item of localArticle.tagNameList"
          :key="item"
          closable
          style="margin:0 1rem 0 0"
          @close="$emit('remove-tag', item)"
        >
          {{ item }}
        </el-tag>
        <el-popover
          v-if="localArticle.tagNameList.length < 3"
          placement="bottom-start"
          width="460"
          trigger="click"
        >
          <div class="popover-title">标签</div>
          <el-autocomplete
            v-model="localTagName"
            style="width:100%"
            :fetch-suggestions="searchTags"
            placeholder="请输入标签名搜索，enter 可添加自定义标签"
            :trigger-on-focus="false"
            @keyup.enter="saveTag"
            @select="handleSelectTag"
          >
            <template #default="{ item }"><div>{{ item.tagName }}</div></template>
          </el-autocomplete>
          <div class="popover-container">
            <div class="popover-subtitle">添加标签</div>
            <button
              v-for="item of tagList"
              :key="item.id || item.tagName"
              type="button"
              :class="tagClass(item)"
              @click="$emit('add-tag', item)"
            >
              {{ item.tagName }}
            </button>
          </div>
          <template #reference><el-button type="primary" plain size="small">添加标签</el-button></template>
        </el-popover>
      </el-form-item>

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
const cloneArticle = article => ({
  ...article,
  tagNameList: [...(article.tagNameList || [])]
});

export default {
  name: "ArticleEditorForm",
  emits: [
    "update:modelValue",
    "save",
    "update-article",
    "search-categories",
    "search-tags",
    "add-category",
    "remove-category",
    "add-tag",
    "remove-tag"
  ],
  props: {
    modelValue: { type: Boolean, default: false },
    article: { type: Object, required: true },
    categoryList: { type: Array, default: () => [] },
    tagList: { type: Array, default: () => [] },
    categoryName: { type: String, default: "" },
    tagName: { type: String, default: "" },
    typeList: { type: Array, default: () => [] },
    beforeUpload: { type: Function, required: true },
    uploadRequest: { type: Function, required: true }
  },
  data() {
    return {
      localCategoryName: this.categoryName,
      localTagName: this.tagName,
      localArticle: cloneArticle(this.article),
      localArticleSerialization: JSON.stringify(cloneArticle(this.article))
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
        this.emitLocalArticle();
      }
    },
    categoryName(value) {
      this.localCategoryName = value;
    },
    tagName(value) {
      this.localTagName = value;
    }
  },
  methods: {
    emitLocalArticle() {
      const next = cloneArticle(this.localArticle);
      const serialization = JSON.stringify(next);
      if (serialization === this.localArticleSerialization) return;
      this.localArticleSerialization = serialization;
      this.$emit("update-article", next);
    },
    searchCategories(keywords, callback) {
      this.$emit("search-categories", keywords, callback);
    },
    searchTags(keywords, callback) {
      this.$emit("search-tags", keywords, callback);
    },
    saveCategory() {
      const value = this.localCategoryName.trim();
      if (!value) return;
      this.$emit("add-category", { categoryName: value });
      this.localCategoryName = "";
    },
    saveTag() {
      const value = this.localTagName.trim();
      if (!value) return;
      this.$emit("add-tag", { tagName: value });
      this.localTagName = "";
    },
    handleSelectCategory(item) {
      this.$emit("add-category", { categoryName: item.categoryName });
    },
    handleSelectTag(item) {
      this.$emit("add-tag", { tagName: item.tagName });
    },
    tagClass(item) {
      return this.localArticle.tagNameList.includes(item.tagName) ? "tag-item-select" : "tag-item";
    }
  }
};
</script>

<style scoped>
.category-item,
.tag-item,
.tag-item-select {
  display: block;
  width: 100%;
  padding: 0.6rem 0.5rem;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.category-item:hover,
.tag-item:hover {
  background-color: #f0f9eb;
  color: #67c23a;
}

.tag-item-select {
  color: #b7beca;
  cursor: not-allowed;
}

.popover-title,
.popover-subtitle {
  margin-bottom: 1rem;
  text-align: center;
}

.popover-container {
  margin-top: 1rem;
  max-height: 260px;
  overflow-y: auto;
}
</style>
