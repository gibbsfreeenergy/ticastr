<template>
  <!-- 搜索框 -->
  <v-dialog v-model="searchFlag" max-width="600" :fullscreen="isMobile">
    <v-card class="search-wrapper" style="border-radius:4px">
      <div class="mb-3">
        <span class="search-title">本地搜索</span>
        <!-- 关闭按钮 -->
        <v-icon class="float-right" @click="searchFlag = false">
          $mdi-close
        </v-icon>
      </div>
      <!-- 输入框 -->
      <div class="search-input-wrapper">
        <v-icon>$mdi-magnify</v-icon>
        <input v-model="keywords" placeholder="输入文章标题或内容..." />
      </div>
      <!-- 搜索结果 -->
      <div class="search-result-wrapper">
        <hr class="divider" />
        <ul>
          <li class="search-reslut" v-for="item of articleList" :key="item.id">
            <!-- 文章标题 -->
            <a @click="goTo(item.id)" v-safe-html="item.articleTitle" />
            <!-- 文章内容 -->
            <p
              class="search-reslut-content text-justify"
              v-safe-html="item.snippet"
            />
          </li>
        </ul>
        <!-- 搜索结果不存在提示 -->
        <p v-if="loading" role="status">搜索中…</p>
        <div v-if="error" role="alert">
          搜索失败，请重试。
          <button type="button" class="search-more" @click="loadSearch(articleList.length > 0)">重试</button>
        </div>
        <button
          v-if="nextCursor && !error"
          type="button"
          class="search-more"
          :disabled="loading"
          @click="loadSearch(true)"
        >加载更多</button>
        <div
          v-show="flag && !loading && !error && articleList.length == 0"
          style="font-size:0.875rem"
        >
          找不到您查询的内容：{{ keywords }}
        </div>
      </div>
    </v-card>
  </v-dialog>
</template>

<script>
export default {
  unmounted() {
    clearTimeout(this.searchTimer);
    this.searchRequestId++;
  },
  data: function() {
    return {
      keywords: "",
      articleList: [],
      flag: false,
      searchTimer: null,
      searchRequestId: 0,
      nextCursor: null,
      loading: false,
      error: false
    };
  },
  methods: {
    goTo(articleId) {
      this.$store.state.searchFlag = false;
      this.$router.push({ path: "/articles/" + articleId });
    },
    async loadSearch(append = false) {
      if (this.loading || !this.keywords.trim()) return;
      const requestId = this.searchRequestId;
      const params = { size: 10, keywords: this.keywords };
      if (append && this.nextCursor) params.cursor = this.nextCursor;
      this.loading = true;
      this.error = false;
      try {
        const data = await this.$api.article.search({ params });
        if (requestId !== this.searchRequestId) return;
        const page = data.data || {};
        this.articleList = append ? [...this.articleList, ...(page.items || [])] : (page.items || []);
        this.nextCursor = page.hasNext ? page.nextCursor : null;
      } catch {
        if (requestId === this.searchRequestId) this.error = true;
      } finally {
        if (requestId === this.searchRequestId) this.loading = false;
      }
    }
  },
  computed: {
    searchFlag: {
      set(value) {
        this.$store.state.searchFlag = value;
      },
      get() {
        return this.$store.state.searchFlag;
      }
    },
    isMobile() {
      const clientWidth = document.documentElement.clientWidth;
      if (clientWidth > 960) {
        return false;
      }
      return true;
    }
  },
  watch: {
    keywords(value) {
      this.flag = value.trim() != "" ? true : false;
      clearTimeout(this.searchTimer);
      ++this.searchRequestId;
      this.articleList = [];
      this.nextCursor = null;
      this.loading = false;
      this.error = false;
      if (!this.flag) {
        return;
      }
      this.searchTimer = setTimeout(() => {
        this.loadSearch();
      }, 240);
    }
  }
};
</script>

<style scoped>
.search-more {
  margin: 12px 0;
  padding: 8px 16px;
  border: 1px solid currentColor;
  border-radius: 4px;
  color: #49b1f5;
  cursor: pointer;
}
.search-more:disabled {
  opacity: 0.6;
  cursor: wait;
}
.search-wrapper {
  padding: 1.25rem;
  height: 100%;
  background: #fff !important;
}
.search-title {
  color: #49b1f5;
  font-size: 1.25rem;
  line-height: 1;
}
.search-input-wrapper {
  display: flex;
  padding: 5px;
  height: 35px;
  width: 100%;
  border: 2px solid #8e8cd8;
  border-radius: 2rem;
}
.search-input-wrapper input {
  width: 100%;
  margin-left: 5px;
  outline: none;
}
@media (min-width: 960px) {
  .search-result-wrapper {
    padding-right: 5px;
    height: 450px;
    overflow: auto;
  }
}
@media (max-width: 959px) {
  .search-result-wrapper {
    height: calc(100vh - 110px);
    overflow: auto;
  }
}
.search-reslut a {
  color: #555;
  font-weight: bold;
  border-bottom: 1px solid #999;
  text-decoration: none;
}
.search-reslut-content {
  color: #555;
  cursor: pointer;
  border-bottom: 1px dashed #ccc;
  padding: 5px 0;
  line-height: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}
.divider {
  margin: 20px 0;
  border: 2px dashed #d2ebfd;
}
</style>
