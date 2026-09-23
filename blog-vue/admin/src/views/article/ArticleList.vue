<template>
  <el-card class="main-card">
    <div class="article-list-header">
      <div>
        <div class="title">文章列表</div>
        <p class="article-list-description">集中管理已发布、私密和草稿文章。</p>
      </div>
      <el-button type="primary" class="publish-article-button" @click="$router.push({ path: '/articles/new' })">
        <AppIcon name="pen" :size="16" />
        <span>发布文章</span>
      </el-button>
    </div>
    <div class="article-status-menu" role="tablist" aria-label="文章状态">
      <span class="status-label">状态</span>
      <div class="status-options">
        <button
          v-for="item in statusOptions"
          :key="item.key"
          type="button"
          role="tab"
          :class="isActive(item.key)"
          :aria-selected="activeStatus === item.key"
          @click="changeStatus(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    <div class="operation-container article-operations">
      <el-button class="bulk-delete" type="danger" size="small" :disabled="articleIdList.length === 0" @click="updateIsDelete = true">
        {{ articleIdList.length ? `删除选中 (${articleIdList.length})` : "批量删除" }}
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

    <el-table class="article-table" border :data="articleList" @selection-change="selectionChange" v-loading="loading">
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

    <div class="mobile-article-list" v-loading="loading" aria-label="文章列表">
      <el-empty v-if="!loading && articleList.length === 0" description="暂无文章" />
      <article v-for="row in articleList" :key="row.id" class="mobile-article-card">
        <div class="mobile-article-card__header">
          <label class="mobile-article-select">
            <input
              type="checkbox"
              :checked="articleIdList.includes(row.id)"
              :aria-label="`选择文章：${row.articleTitle}`"
              @change="toggleMobileSelection(row.id, $event)"
            />
            <span>选择</span>
          </label>
          <el-tag :type="articleType(row.type).tagType">{{ articleType(row.type).name }}</el-tag>
        </div>
        <div class="mobile-article-card__body">
          <el-image class="mobile-article-cover" :src="row.articleCover || fallbackCover" fit="cover" />
          <div class="mobile-article-copy">
            <h3>{{ row.articleTitle }}</h3>
            <p>{{ date(row.createTime) }}</p>
          </div>
        </div>
        <div class="mobile-article-card__footer">
          <span v-if="row.isDelete === 1" class="mobile-article-deleted">已在回收站</span>
          <span v-else>{{ row.isTop === 1 ? "已置顶" : "未置顶" }}</span>
          <div class="mobile-article-actions">
            <el-button v-if="row.isDelete === 0" type="primary" size="small" @click="editArticle(row.id)">编辑</el-button>
            <el-button v-if="row.isDelete === 0" type="danger" size="small" @click="updateArticleDelete(row.id)">删除</el-button>
            <template v-else>
              <el-button type="success" size="small" @click="updateArticleDelete(row.id)">恢复</el-button>
              <el-button type="danger" size="small" @click="deleteArticles(row.id)">彻底删除</el-button>
            </template>
          </div>
        </div>
      </article>
    </div>

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
import AppIcon from "../../components/AppIcon.vue";

export default {
  name: "ArticleList",
  components: { AppIcon },
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
    toggleMobileSelection(id, event) {
      const selectedIds = new Set(this.articleIdList);
      if (event.target.checked) selectedIds.add(id);
      else selectedIds.delete(id);
      this.articleIdList = Array.from(selectedIds);
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
.article-list-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.5rem;
}

.article-list-header .title {
  margin-bottom: 8px;
}

.article-list-description {
  margin: 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.publish-article-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  min-height: 40px;
  padding-right: 16px;
  padding-left: 16px;
}

.publish-article-button :deep(.app-icon) {
  margin-right: 0;
}

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
  margin: 22px 0 20px;
  color: #999;
  font-size: 14px;
}

.status-options {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.status-options button {
  min-height: 36px;
  padding: 6px 11px;
  color: #86868b;
  font: inherit;
  font-size: 12px;
  background: #f7f7fa;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.status-options button:hover,
.status-options button:focus-visible {
  color: #6e6e73;
  border-color: #d2d2d7;
}

.status,
.active-status {
  cursor: pointer;
}

.active-status {
  color: #0071e3 !important;
  font-weight: bold;
  background: #eaf3ff !important;
  border-color: rgba(0, 113, 227, 0.12) !important;
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

.mobile-article-list {
  display: none;
}

.mobile-article-card {
  padding: 13px;
  background: #fbfbfd;
  border: 1px solid #e5e5ea;
  border-radius: 14px;
}

.mobile-article-card__header,
.mobile-article-card__body,
.mobile-article-card__footer,
.mobile-article-select,
.mobile-article-actions {
  display: flex;
  align-items: center;
}

.mobile-article-card__header,
.mobile-article-card__footer {
  justify-content: space-between;
  gap: 10px;
}

.mobile-article-card__body {
  min-width: 0;
  gap: 13px;
  padding: 13px 0;
}

.mobile-article-select {
  gap: 8px;
  min-height: 36px;
  color: #6e6e73;
  font-size: 12px;
  cursor: pointer;
}

.mobile-article-select input {
  width: 20px;
  height: 20px;
  margin: 0;
  accent-color: #0071e3;
}

.mobile-article-cover {
  flex: 0 0 92px;
  width: 92px;
  height: 64px;
  overflow: hidden;
  border-radius: 9px;
}

.mobile-article-copy {
  min-width: 0;
}

.mobile-article-copy h3 {
  display: -webkit-box;
  margin: 0 0 5px;
  overflow: hidden;
  color: #1d1d1f;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.45;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mobile-article-copy p,
.mobile-article-card__footer > span {
  margin: 0;
  color: #86868b;
  font-size: 11px;
}

.mobile-article-deleted {
  color: #c9342b !important;
}

.mobile-article-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 7px;
}

.mobile-article-actions .el-button {
  margin: 0;
}

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .article-list-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .publish-article-button {
    width: 100%;
    justify-content: center;
  }

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

@media (max-width: 900px), (hover: none) and (pointer: coarse) {
  .article-status-menu {
    display: block;
    margin: 16px 0 20px !important;
  }

  .article-status-menu .status-label {
    display: block;
    margin-bottom: 8px;
  }

  .article-status-menu .status-options {
    display: grid !important;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .status-options button {
    width: 100%;
    padding-right: 5px;
    padding-left: 5px;
  }

  .article-operations .bulk-delete {
    order: 2;
    width: 100%;
  }

  .article-operations .filters {
    order: 1;
  }

  .article-operations .filters .el-select {
    width: 100%;
  }

  .article-table {
    display: none;
  }

  .mobile-article-list {
    display: grid;
    min-height: 120px;
    gap: 10px;
  }

  .mobile-article-list > .el-empty {
    padding: 24px 0;
  }

  .pagination-container {
    margin-top: 16px;
  }
}
</style>
