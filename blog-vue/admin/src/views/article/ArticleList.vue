<template>
  <el-card class="main-card">
    <div class="title">文章列表</div>
    <div class="article-status-menu">
      <span>状态</span>
      <span v-for="item in statusOptions" :key="item.key" :class="isActive(item.key)" @click="changeStatus(item.key)">
        {{ item.label }}
      </span>
    </div>
    <div class="operation-container">
      <el-button type="danger" size="small" :disabled="articleIdList.length === 0" @click="updateIsDelete = true">
        批量删除
      </el-button>
      <div class="filters">
        <el-select v-model="type" clearable placeholder="文章类型" size="small">
          <el-option v-for="item in typeList" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="keywords"
          clearable
          prefix-icon="el-icon-search"
          size="small"
          placeholder="搜索文章标题"
          @keyup.enter="searchArticles"
        />
        <el-button type="primary" size="small" @click="searchArticles">搜索</el-button>
      </div>
    </div>

    <el-table border :data="articleList" @selection-change="selectionChange" v-loading="loading">
      <el-table-column type="selection" width="55" />
      <el-table-column prop="articleCover" label="封面" width="150" align="center">
        <template #default="{ row }">
          <el-image class="article-cover" :src="row.articleCover || fallbackCover" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="articleTitle" label="标题" min-width="240" />
      <el-table-column prop="type" label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="articleType(row.type).tagType">{{ articleType(row.type).name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发表时间" width="150" align="center">
        <template #default="{ row }">{{ date(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="isTop" label="置顶" width="80" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.isTop" :active-value="1" :inactive-value="0" :disabled="row.isDelete === 1" @change="changeTop(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.isDelete === 0" type="primary" size="small" @click="editArticle(row.id)">编辑</el-button>
          <el-button v-if="row.isDelete === 0" type="danger" size="small" @click="updateArticleDelete(row.id)">删除</el-button>
          <template v-else>
            <el-button type="success" size="small" @click="updateArticleDelete(row.id)">恢复</el-button>
            <el-button type="danger" size="small" @click="deleteArticles(row.id)">彻底删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination-container"
      background
      v-model:current-page="current"
      v-model:page-size="size"
      :total="count"
      :page-sizes="[10, 20]"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="listArticles"
      @current-change="listArticles"
    />

    <el-dialog v-model="updateIsDelete" width="360px" title="确认删除">
      <p>是否删除选中文章？</p>
      <template #footer>
        <el-button @click="updateIsDelete = false">取消</el-button>
        <el-button type="primary" @click="updateArticleDelete()">确认</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script>
export default {
  name: "ArticleList",
  data() {
    return {
      loading: true,
      updateIsDelete: false,
      activeStatus: "all",
      articleList: [],
      articleIdList: [],
      keywords: "",
      type: null,
      isDelete: 0,
      status: null,
      current: 1,
      size: 10,
      count: 0,
      typeList: [
        { value: 1, label: "原创" },
        { value: 2, label: "转载" },
        { value: 3, label: "翻译" }
      ],
      statusOptions: [
        { key: "all", label: "全部" },
        { key: "public", label: "公开" },
        { key: "secret", label: "私密" },
        { key: "draft", label: "草稿箱" },
        { key: "delete", label: "回收站" }
      ],
      fallbackCover: "/images/default-image.svg"
    };
  },
  created() {
    this.listArticles();
  },
  methods: {
    async listArticles() {
      this.loading = true;
      try {
        const response = await this.$api.article.adminList({
          params: {
            current: this.current,
            size: this.size,
            keywords: this.keywords || undefined,
            status: this.status,
            type: this.type,
            isDelete: this.isDelete
          }
        });
        this.articleList = response.data?.recordList || [];
        this.count = response.data?.count || 0;
      } finally {
        this.loading = false;
      }
    },
    searchArticles() {
      this.current = 1;
      return this.listArticles();
    },
    selectionChange(rows) {
      this.articleIdList = rows.map(item => item.id);
    },
    editArticle(id) {
      this.$router.push({ path: "/articles/" + id });
    },
    changeStatus(status) {
      this.activeStatus = status;
      this.isDelete = status === "delete" ? 1 : 0;
      this.status = { public: 1, secret: 2, draft: 3 }[status] || null;
      this.current = 1;
      this.listArticles();
    },
    updateArticleDelete(id) {
      const idList = id == null ? this.articleIdList : [id];
      if (!idList.length) return;
      this.$api.article.updateDelete({ idList, isDelete: this.isDelete === 0 ? 1 : 0 }).then(() => {
        this.updateIsDelete = false;
        this.listArticles();
      });
    },
    deleteArticles(id) {
      const idList = id == null ? this.articleIdList : [id];
      if (!idList.length) return;
      this.$api.article.remove({ data: idList }).then(() => this.listArticles());
    },
    changeTop(article) {
      this.$api.article.updateTop({ id: article.id, isTop: article.isTop }).then(() => this.listArticles());
    }
  },
  computed: {
    articleType() {
      return type => ({
        1: { tagType: "danger", name: "原创" },
        2: { tagType: "success", name: "转载" },
        3: { tagType: "primary", name: "翻译" }
      }[type] || { tagType: "info", name: "未知" });
    },
    isActive() {
      return status => this.activeStatus === status ? "active-status" : "status";
    }
  }
};
</script>

<style scoped>
.operation-container {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 1.5rem;
}

.filters {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-left: auto;
}

.filters .el-input {
  width: 220px;
}

.article-status-menu {
  margin-top: 40px;
  color: #999;
  font-size: 14px;
}

.article-status-menu span {
  margin-right: 24px;
}

.status,
.active-status {
  cursor: pointer;
}

.active-status {
  color: #333;
  font-weight: bold;
}

.article-cover {
  width: 110px;
  height: 68px;
  border-radius: 4px;
}

.pagination-container {
  justify-content: flex-end;
  margin-top: 1rem;
}

@media (max-width: 720px) {
  .operation-container,
  .filters {
    align-items: stretch;
    flex-direction: column;
  }

  .filters {
    width: 100%;
    margin-left: 0;
  }

  .filters .el-input {
    width: 100%;
  }
}
</style>
