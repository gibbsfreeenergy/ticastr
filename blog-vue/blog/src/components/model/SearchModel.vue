<template>
  <v-dialog v-model="searchFlag" max-width="720" :fullscreen="isMobile" content-class="search-dialog">
    <v-card class="search-panel">
      <div class="search-head">
        <div>
          <span class="search-kicker">SEARCH</span>
          <h2>找一篇想读的文章</h2>
        </div>
        <span class="search-close" role="button" tabindex="0" aria-label="关闭搜索" @click="searchFlag = false">×</span>
      </div>

      <label class="search-input-wrapper">
        <NavIcon name="search" />
        <input ref="searchInput" v-model="keywords" autofocus placeholder="输入文章标题或内容…" />
        <kbd>⌘ K</kbd>
      </label>

      <div class="search-result-wrapper">
        <div v-if="loading && !articleList.length" class="search-status" role="status">正在寻找文字…</div>
        <div v-if="error" class="search-status" role="alert">
          搜索失败，请重试。
          <button type="button" class="search-more" @click="loadSearch(true)">重试</button>
        </div>
        <div v-if="articleList.length" class="search-result-count">找到 {{ articleList.length }} 篇相关内容</div>
        <ul v-if="articleList.length" class="search-results">
          <li v-for="item of articleList" :key="item.id" class="search-result-item">
            <a @click="goTo(item.id)" v-safe-html="item.articleTitle" />
            <p v-safe-html="item.snippet" />
          </li>
        </ul>
        <div v-else-if="!loading && !error && flag && keywords" class="search-status">找不到与“{{ keywords }}”相关的内容。</div>
        <div v-else-if="!loading && !error" class="search-empty">
          <span>✦</span>
          <p>输入几个词，让记忆自己浮上来。</p>
        </div>
        <button
          v-if="nextCursor && !error && !loading"
          type="button"
          class="search-more"
          @click="loadSearch(true)"
        >加载更多</button>
      </div>
    </v-card>
  </v-dialog>
</template>

<script>
import NavIcon from "../NavIcon.vue";

export default {
  name: "SearchModel",
  components: { NavIcon },
  unmounted() {
    clearTimeout(this.searchTimer);
    this.searchRequestId++;
  },
  data() {
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
      return document.documentElement.clientWidth <= 760;
    }
  },
  watch: {
    keywords(value) {
      this.flag = value.trim() !== "";
      clearTimeout(this.searchTimer);
      ++this.searchRequestId;
      this.articleList = [];
      this.nextCursor = null;
      this.loading = false;
      this.error = false;
      if (!this.flag) return;
      this.searchTimer = setTimeout(() => this.loadSearch(), 240);
    },
    searchFlag(value) {
      if (value) this.$nextTick(() => this.$refs.searchInput?.focus());
    }
  }
};
</script>

<style scoped>
.search-panel {
  padding: 32px;
  border-radius: var(--radius-lg) !important;
  background: var(--paper-strong) !important;
  color: var(--ink);
}

.search-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.search-kicker {
  color: var(--sage-deep);
  font-size: 10px;
  letter-spacing: 0.2em;
}

.search-head h2 {
  margin: 9px 0 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: 27px;
  font-weight: 500;
}

.search-close {
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 30px;
  font-weight: 300;
  line-height: 1;
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 29px;
  padding: 0 15px;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  color: var(--sage-deep);
  background: var(--paper);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.search-input-wrapper:focus-within {
  border-color: var(--sage);
  box-shadow: 0 0 0 4px rgba(143, 169, 154, 0.12);
}

.search-input-wrapper :deep(.nav-icon-svg) {
  flex: 0 0 17px;
  width: 17px;
  height: 17px;
}

.search-input-wrapper input {
  width: 100%;
  min-height: 48px;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 14px;
}

.search-input-wrapper kbd {
  padding: 3px 7px;
  border: 1px solid var(--line);
  border-radius: 5px;
  color: var(--muted);
  background: var(--paper-strong);
  font-size: 10px;
  white-space: nowrap;
}

.search-result-wrapper {
  max-height: min(54vh, 480px);
  margin-top: 25px;
  overflow-y: auto;
}

.search-result-count {
  margin-bottom: 9px;
  color: var(--muted);
  font-size: 12px;
}

.search-results {
  list-style: none;
}

.search-result-item {
  padding: 15px 0;
  border-bottom: 1px solid var(--line);
}

.search-result-item a {
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 17px;
}

.search-result-item a:hover {
  color: var(--sage-deep);
}

.search-result-item p {
  margin: 6px 0 0 !important;
  overflow: hidden;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-status,
.search-empty {
  padding: 48px 20px;
  color: var(--muted);
  text-align: center;
}

.search-empty span {
  color: var(--gold);
  font-size: 24px;
}

.search-empty p {
  margin: 12px 0 0 !important;
  font-size: 13px;
}

.search-more {
  margin: 18px 0 4px;
  padding: 8px 16px;
  border: 1px solid var(--sage);
  border-radius: 999px;
  color: var(--sage-deep);
  background: transparent;
  font-size: 12px;
}

.search-more:hover {
  color: #fff;
  background: var(--sage-deep);
}

@media (max-width: 760px) {
  .search-panel {
    min-height: 100%;
    padding: 26px 20px;
    border-radius: 0 !important;
  }

  .search-head h2 {
    font-size: 24px;
  }
}
</style>
