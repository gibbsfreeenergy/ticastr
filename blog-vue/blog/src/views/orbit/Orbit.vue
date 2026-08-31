<template>
  <main class="orbit-page" aria-labelledby="orbit-title">
    <div class="orbit-shell">
      <header class="orbit-header">
        <div class="orbit-heading-row">
          <div>
            <p class="orbit-kicker">TICASTR / ORBIT</p>
            <h1 id="orbit-title">阅读星图</h1>
            <p class="orbit-lede">把写过的东西，放回同一片天空。</p>
          </div>
          <div class="orbit-count" aria-live="polite">
            <span>当前视野</span>
            <strong>{{ visibleArticles.length }}</strong>
            <span>/ {{ articles.length }} 篇</span>
          </div>
        </div>

        <div class="orbit-toolbar">
          <label class="orbit-search">
            <span class="sr-only">搜索文章</span>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4.2 4.2" />
            </svg>
            <input
              v-model="query"
              type="search"
              autocomplete="off"
              placeholder="搜索标题、标签或分类"
              @keydown.esc="clearSearch"
            />
            <button
              v-if="query"
              class="clear-search"
              type="button"
              aria-label="清除搜索"
              @click="clearSearch"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m7 7 10 10M17 7 7 17" />
              </svg>
            </button>
          </label>
          <div class="orbit-sort" role="group" aria-label="排序方式">
            <button
              v-for="option in sortOptions"
              :key="option.value"
              type="button"
              :class="{ active: sortMode === option.value }"
              @click="sortMode = option.value"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="orbit-category-row">
          <span class="orbit-category-label">按主题</span>
          <nav class="orbit-categories" aria-label="文章分类">
            <button
              type="button"
              :class="{ active: category === allCategory }"
              @click="category = allCategory"
            >
              {{ allCategory }}
            </button>
            <button
              v-for="item in categories"
              :key="item"
              type="button"
              :class="{ active: category === item }"
              @click="category = item"
            >
              {{ item }}
            </button>
          </nav>
        </div>
      </header>

      <div class="orbit-content">
        <section class="orbit-visual" aria-labelledby="orbit-field-title">
          <div class="visual-heading">
            <div>
              <span class="visual-label" id="orbit-field-title">文章轨道</span>
              <span class="visual-subtitle">每颗星都是一篇文章</span>
            </div>
            <button
              class="refresh-button"
              type="button"
              :disabled="loading"
              aria-label="重新读取文章"
              @click="loadArticles"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M20 11a8 8 0 0 0-14.9-3.9L3 10" />
                <path d="M3 5v5h5M4 13a8 8 0 0 0 14.9 3.9L21 14" />
                <path d="M21 19v-5h-5" />
              </svg>
              <span>刷新</span>
            </button>
          </div>

          <div v-if="loading" class="orbit-state" role="status" aria-live="polite">
            <span class="state-orb" aria-hidden="true" />
            <strong>正在校准星图…</strong>
            <span>读取文章元数据</span>
          </div>

          <div v-else-if="error" class="orbit-state orbit-state-error" role="alert">
            <span class="state-mark" aria-hidden="true">!</span>
            <strong>暂时无法读取文章元数据，请稍后重试。</strong>
            <button data-testid="retry-articles" type="button" @click="loadArticles">重新连接</button>
          </div>

          <div v-else-if="!articles.length" class="orbit-state orbit-state-empty">
            <span class="state-orb" aria-hidden="true" />
            <strong>还没有可以观测的文章。</strong>
            <span>当第一篇文章出现，它会在这里亮起来。</span>
          </div>

          <div v-else-if="!visibleArticles.length" class="orbit-state orbit-state-empty">
            <span class="state-mark" aria-hidden="true">⌕</span>
            <strong>没有找到这颗星。</strong>
            <span>换个关键词，或清除当前筛选。</span>
            <button type="button" @click="resetFilters">清除筛选</button>
          </div>

          <template v-else>
            <div class="orbit-stage" aria-describedby="orbit-stage-hint">
              <span id="orbit-stage-hint" class="sr-only">点击星点查看文章详情</span>
              <div class="stage-grid" aria-hidden="true" />
              <div class="stage-stars" aria-hidden="true">
                <span
                  v-for="star in stars"
                  :key="star.id"
                  class="stage-star"
                  :style="{ left: star.left, top: star.top, width: star.size, height: star.size, animationDelay: star.delay }"
                />
              </div>
              <div class="orbit-rings" aria-hidden="true">
                <span class="orbit-ring orbit-ring-one" />
                <span class="orbit-ring orbit-ring-two" />
                <span class="orbit-ring orbit-ring-three" />
              </div>
              <svg class="orbit-lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <line
                  v-for="article in positionedArticles"
                  :key="article.id"
                  x1="50"
                  y1="50"
                  :x2="article.position.x"
                  :y2="article.position.y"
                  :class="{ selected: selectedArticle && article.id === selectedArticle.id }"
                />
                <circle cx="50" cy="50" r="1.6" />
              </svg>
              <div class="orbit-core" aria-hidden="true">
                <span class="core-halo" />
                <span class="core-dot" />
                <span class="core-label">TICASTR</span>
              </div>
              <button
                v-for="(article, index) in positionedArticles"
                :key="article.id"
                class="orbit-node"
                :class="{ selected: selectedArticle && article.id === selectedArticle.id, ['tone-' + (index % 3)]: true }"
                :style="{ left: article.position.left, top: article.position.top }"
                :data-article-id="article.id"
                type="button"
                :aria-label="article.title + '，' + article.categoryName"
                :aria-pressed="selectedArticle && article.id === selectedArticle.id"
                @click="selectArticle(article)"
              >
                <span class="node-dot"><span /></span>
                <span class="node-copy">
                  <strong>{{ article.title }}</strong>
                  <small>{{ article.categoryName }}</small>
                </span>
              </button>
            </div>

            <div class="orbit-list" aria-label="文章列表">
              <button
                v-for="(article, index) in visibleArticles"
                :key="article.id"
                class="orbit-list-item"
                :class="{ selected: selectedArticle && article.id === selectedArticle.id }"
                :data-article-id="article.id"
                type="button"
                :aria-pressed="selectedArticle && article.id === selectedArticle.id"
                @click="selectArticle(article)"
              >
                <span class="list-index">{{ String(index + 1).padStart(2, "0") }}</span>
                <span class="list-copy">
                  <strong>{{ article.title }}</strong>
                  <small>{{ article.categoryName }} · {{ formatDate(article.createTime) }}</small>
                </span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M5 12h13M13 6l6 6-6 6" />
                </svg>
              </button>
            </div>
          </template>

          <div v-if="visibleArticles.length" class="visual-footnote">
            <span><i class="status-dot" /> 元数据已同步</span>
            <span>{{ visibleArticles.length }} 个信号正在视野内</span>
          </div>
        </section>

        <aside class="detail-rail" aria-label="文章详情">
          <template v-if="selectedArticle">
            <div class="detail-heading">
              <div>
                <span class="detail-label">SELECTED SIGNAL</span>
                <span class="detail-number">{{ String(selectedIndex + 1).padStart(2, "0") }}</span>
              </div>
              <button class="surprise-button" type="button" @click="surpriseMe">
                换一个
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M4 7h3c4.8 0 5.2 10 10 10h3M4 17h3c1.6 0 2.8-1.3 3.6-2.8M14.4 9.8C15.2 8.2 16.4 7 18 7h2" />
                  <path d="m17 4 3 3-3 3M17 14l3 3-3 3" />
                </svg>
              </button>
            </div>
            <div class="detail-category">
              <span class="detail-dot" />
              {{ selectedArticle.categoryName }}
            </div>
            <h2 data-testid="selected-title">{{ selectedArticle.title }}</h2>
            <p class="detail-summary">{{ selectedArticle.summary || "打开文章，阅读完整内容。" }}</p>
            <div class="detail-meta">
              <span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="8" />
                  <path d="M12 7v5l3 2" />
                </svg>
                {{ selectedArticle.readingMinutes }}分钟阅读
              </span>
              <span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="4" y="5" width="16" height="15" rx="2" />
                  <path d="M8 3v4M16 3v4M4 10h16" />
                </svg>
                {{ formatDate(selectedArticle.createTime) }}
              </span>
            </div>
            <div v-if="selectedArticle.tags.length" class="detail-tags" aria-label="文章标签">
              <span v-for="tag in selectedArticle.tags" :key="tag">#{{ tag }}</span>
            </div>
            <router-link
              class="detail-link"
              data-testid="router-link"
              :to="'/articles/' + selectedArticle.id"
            >
              打开文章
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h13M13 6l6 6-6 6" />
              </svg>
            </router-link>
          </template>
          <template v-else>
            <span class="detail-label">OBSERVATORY LOG</span>
            <h2>等一束新的信号</h2>
            <p class="detail-summary">文章出现后，选中任意星点，这里会显示它的轨道信息。</p>
          </template>
        </aside>
      </div>
    </div>
  </main>
</template>

<script>
import {
  chooseSurprise,
  filterOrbitArticles,
  getOrbitCategories,
  getOrbitPosition,
  normalizeArticles,
  sortOrbitArticles
} from "./orbitState";

export default {
  name: "OrbitPage",
  data() {
    return {
      articles: [],
      query: "",
      category: "全部",
      sortMode: "latest",
      selectedId: null,
      loading: false,
      error: false,
      sortOptions: [
        { value: "latest", label: "最新" },
        { value: "oldest", label: "最早" },
        { value: "reading", label: "阅读时间" }
      ],
      stars: Array.from({ length: 30 }, (_, index) => ({
        id: index,
        left: `${(index * 37 + 7) % 94 + 3}%`,
        top: `${(index * 61 + 13) % 86 + 7}%`,
        size: `${index % 3 === 0 ? 3 : 2}px`,
        delay: `${(index % 8) * 0.45}s`
      }))
    };
  },
  created() {
    this.loadArticles();
  },
  computed: {
    allCategory() {
      return "全部";
    },
    categories() {
      return getOrbitCategories(this.articles);
    },
    visibleArticles() {
      const filtered = filterOrbitArticles(this.articles, {
        query: this.query,
        category: this.category
      });
      return sortOrbitArticles(filtered, this.sortMode);
    },
    positionedArticles() {
      return this.visibleArticles.map((article, index) => ({
        ...article,
        position: getOrbitPosition(index, this.visibleArticles.length)
      }));
    },
    selectedArticle() {
      return this.visibleArticles.find(article => String(article.id) === String(this.selectedId))
        || this.visibleArticles[0]
        || null;
    },
    selectedIndex() {
      if (!this.selectedArticle) return 0;
      return Math.max(0, this.visibleArticles.findIndex(article => article.id === this.selectedArticle.id));
    }
  },
  watch: {
    visibleArticles(next) {
      if (!next.some(article => String(article.id) === String(this.selectedId))) {
        this.selectedId = next[0]?.id ?? null;
      }
    }
  },
  methods: {
    async loadArticles() {
      this.loading = true;
      this.error = false;
      try {
        const response = await this.$api.article.home({
          params: { size: 50 },
          suppressErrorToast: true,
          timeout: 5000
        });
        const page = response?.data;
        const items = Array.isArray(page) ? page : page?.items;
        this.articles = normalizeArticles(items);
        this.selectedId = this.articles[0]?.id ?? null;
      } catch {
        this.error = true;
      } finally {
        this.loading = false;
      }
    },
    selectArticle(article) {
      this.selectedId = article.id;
    },
    surpriseMe() {
      const article = chooseSurprise(this.visibleArticles, this.selectedId);
      if (article) this.selectedId = article.id;
    },
    clearSearch() {
      this.query = "";
    },
    resetFilters() {
      this.query = "";
      this.category = this.allCategory;
    },
    formatDate(value) {
      if (!value) return "日期未记录";
      return typeof this.date === "function" ? this.date(value) : String(value).slice(0, 10);
    }
  }
};
</script>

<style scoped>
.orbit-page {
  --orbit-bg: #0d1229;
  --orbit-surface: #151b3b;
  --orbit-surface-soft: rgba(30, 38, 78, 0.72);
  --orbit-border: rgba(169, 183, 255, 0.18);
  --orbit-text: #f4f5ff;
  --orbit-muted: #9da8cf;
  --orbit-faint: #66719e;
  --orbit-gold: #f6c978;
  --orbit-blue: #8ca8ff;
  position: relative;
  min-height: calc(100vh - 60px);
  overflow: hidden;
  background: var(--orbit-bg);
  color: var(--orbit-text);
}
.orbit-page::before,
.orbit-page::after {
  position: absolute;
  pointer-events: none;
  content: "";
}
.orbit-page::before {
  top: -20rem;
  right: -10rem;
  width: 42rem;
  height: 42rem;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(89, 108, 222, 0.22), transparent 68%);
}
.orbit-page::after {
  bottom: -24rem;
  left: -18rem;
  width: 42rem;
  height: 42rem;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(199, 126, 190, 0.1), transparent 68%);
}
.orbit-shell {
  position: relative;
  z-index: 1;
  max-width: 1320px;
  margin: 0 auto;
  padding: 68px 32px 92px;
}
.orbit-header {
  margin-bottom: 34px;
}
.orbit-heading-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}
.orbit-kicker,
.detail-label,
.visual-label {
  margin: 0;
  color: var(--orbit-gold);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2em;
  line-height: 1.4;
  text-transform: uppercase;
}
.orbit-heading-row h1 {
  margin: 14px 0 8px;
  color: var(--orbit-text);
  font-size: clamp(2.8rem, 6vw, 5.8rem);
  font-weight: 700;
  letter-spacing: -0.065em;
  line-height: 0.95;
}
.orbit-lede {
  margin: 0;
  color: var(--orbit-muted);
  font-size: 1.05rem;
  letter-spacing: 0.04em;
}
.orbit-count {
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding-bottom: 8px;
  color: var(--orbit-faint);
  font-size: 12px;
  white-space: nowrap;
}
.orbit-count strong {
  color: var(--orbit-text);
  font-size: 2.2rem;
  font-weight: 500;
  letter-spacing: -0.06em;
}
.orbit-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 42px;
}
.orbit-search {
  display: flex;
  flex: 1 1 360px;
  align-items: center;
  max-width: 510px;
  min-height: 50px;
  padding: 0 16px;
  border: 1px solid var(--orbit-border);
  border-radius: 13px;
  background: rgba(20, 26, 57, 0.72);
  transition: border-color 0.25s ease, background 0.25s ease;
}
.orbit-search:focus-within {
  border-color: rgba(246, 201, 120, 0.72);
  background: rgba(24, 31, 67, 0.92);
}
.orbit-search > svg {
  width: 19px;
  height: 19px;
  margin-right: 11px;
  fill: none;
  stroke: var(--orbit-muted);
  stroke-linecap: round;
  stroke-width: 1.7;
}
.orbit-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--orbit-text);
  font: inherit;
  font-size: 14px;
}
.orbit-search input::placeholder {
  color: var(--orbit-faint);
}
.clear-search,
.refresh-button,
.surprise-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: var(--orbit-muted);
  cursor: pointer;
  font: inherit;
}
.clear-search {
  flex: 0 0 auto;
  width: 25px;
  height: 25px;
  padding: 4px;
  border-radius: 50%;
}
.clear-search:hover,
.clear-search:focus-visible {
  background: rgba(255, 255, 255, 0.09);
  color: var(--orbit-text);
}
.clear-search svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-width: 1.7;
}
.orbit-sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--orbit-border);
  border-radius: 12px;
  background: rgba(20, 26, 57, 0.48);
}
.orbit-sort button,
.orbit-categories button {
  border: 0;
  background: transparent;
  color: var(--orbit-faint);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: color 0.2s ease, background 0.2s ease;
}
.orbit-sort button {
  min-height: 38px;
  padding: 0 15px;
  border-radius: 8px;
}
.orbit-sort button:hover,
.orbit-sort button:focus-visible,
.orbit-sort button.active {
  background: rgba(140, 168, 255, 0.14);
  color: var(--orbit-text);
}
.orbit-category-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 24px;
}
.orbit-category-label {
  flex: 0 0 auto;
  color: var(--orbit-faint);
  font-size: 12px;
}
.orbit-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}
.orbit-categories button {
  min-height: 31px;
  padding: 0 13px;
  border: 1px solid transparent;
  border-radius: 99px;
}
.orbit-categories button:hover,
.orbit-categories button:focus-visible,
.orbit-categories button.active {
  border-color: rgba(246, 201, 120, 0.34);
  background: rgba(246, 201, 120, 0.12);
  color: var(--orbit-gold);
}
.orbit-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 302px;
  align-items: start;
  gap: 22px;
}
.orbit-visual,
.detail-rail {
  border: 1px solid var(--orbit-border);
  background: var(--orbit-surface-soft);
  box-shadow: 0 22px 60px rgba(4, 7, 24, 0.24);
}
.orbit-visual {
  min-width: 0;
  padding: 22px;
  border-radius: 25px;
}
.visual-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 4px 18px;
}
.visual-heading > div {
  display: flex;
  align-items: baseline;
  gap: 13px;
}
.visual-subtitle {
  color: var(--orbit-faint);
  font-size: 12px;
}
.refresh-button {
  gap: 6px;
  padding: 6px 2px 6px 8px;
  color: var(--orbit-muted);
  font-size: 12px;
}
.refresh-button:hover:not(:disabled),
.refresh-button:focus-visible:not(:disabled) {
  color: var(--orbit-gold);
}
.refresh-button:disabled {
  cursor: wait;
  opacity: 0.5;
}
.refresh-button svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}
.orbit-stage {
  position: relative;
  min-height: 560px;
  overflow: hidden;
  border: 1px solid rgba(169, 183, 255, 0.1);
  border-radius: 18px;
  background: radial-gradient(circle at 50% 50%, rgba(81, 101, 217, 0.2), transparent 30%), #10152f;
}
.stage-grid,
.stage-stars,
.orbit-rings,
.orbit-lines,
.orbit-core {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.stage-grid {
  opacity: 0.52;
  background-image: linear-gradient(rgba(160, 180, 255, 0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(160, 180, 255, 0.045) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at center, black 0, transparent 78%);
}
.stage-grid::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 49.9%, rgba(246, 201, 120, 0.14) 50%, transparent 50.1%), linear-gradient(0deg, transparent 49.9%, rgba(246, 201, 120, 0.14) 50%, transparent 50.1%);
  content: "";
}
.stage-star {
  position: absolute;
  border-radius: 50%;
  background: #dbe1ff;
  box-shadow: 0 0 9px rgba(219, 225, 255, 0.68);
  opacity: 0.4;
  animation: star-drift 5s ease-in-out infinite alternate;
}
.orbit-rings {
  display: grid;
  place-items: center;
}
.orbit-ring {
  position: absolute;
  border: 1px solid rgba(140, 168, 255, 0.13);
  border-radius: 50%;
  transform: rotate(-10deg) scaleY(0.72);
}
.orbit-ring-one {
  width: 31%;
  height: 31%;
}
.orbit-ring-two {
  width: 59%;
  height: 59%;
}
.orbit-ring-three {
  width: 88%;
  height: 88%;
  border-style: dashed;
  opacity: 0.7;
}
.orbit-lines {
  overflow: visible;
  width: 100%;
  height: 100%;
}
.orbit-lines line {
  stroke: rgba(140, 168, 255, 0.16);
  stroke-dasharray: 1.2 2.7;
  stroke-width: 0.18;
  vector-effect: non-scaling-stroke;
}
.orbit-lines line.selected {
  stroke: rgba(246, 201, 120, 0.7);
  stroke-dasharray: none;
  stroke-width: 0.45;
}
.orbit-lines circle {
  fill: var(--orbit-gold);
  filter: drop-shadow(0 0 5px rgba(246, 201, 120, 0.8));
}
.orbit-core {
  display: grid;
  place-items: center;
}
.core-halo,
.core-dot {
  position: absolute;
  border-radius: 50%;
}
.core-halo {
  width: 38px;
  height: 38px;
  border: 1px solid rgba(246, 201, 120, 0.33);
  box-shadow: 0 0 26px rgba(246, 201, 120, 0.22);
}
.core-dot {
  width: 8px;
  height: 8px;
  background: var(--orbit-gold);
  box-shadow: 0 0 14px 4px rgba(246, 201, 120, 0.42);
}
.core-label {
  position: absolute;
  margin-top: 62px;
  color: rgba(246, 201, 120, 0.68);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.24em;
}
.orbit-node {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: center;
  max-width: 192px;
  padding: 5px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--orbit-text);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transform: translate(-50%, -50%);
}
.orbit-node:focus-visible {
  outline: 2px solid var(--orbit-gold);
  outline-offset: 4px;
}
.node-dot {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 13px;
  height: 13px;
  border: 1px solid rgba(140, 168, 255, 0.62);
  border-radius: 50%;
  background: #263568;
  box-shadow: 0 0 0 4px rgba(140, 168, 255, 0.06), 0 0 14px rgba(140, 168, 255, 0.32);
  transition: width 0.25s ease, height 0.25s ease, border-color 0.25s ease, background 0.25s ease, box-shadow 0.25s ease;
}
.node-dot span {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--orbit-blue);
}
.node-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-left: 9px;
  opacity: 0.68;
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.node-copy strong {
  overflow: hidden;
  color: var(--orbit-text);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-copy small {
  margin-top: 2px;
  color: var(--orbit-muted);
  font-size: 9px;
  line-height: 1.2;
}
.orbit-node:hover .node-copy,
.orbit-node.selected .node-copy {
  opacity: 1;
}
.orbit-node:hover .node-dot,
.orbit-node.selected .node-dot {
  width: 19px;
  height: 19px;
  border-color: var(--orbit-gold);
  background: #453a3e;
  box-shadow: 0 0 0 5px rgba(246, 201, 120, 0.1), 0 0 22px rgba(246, 201, 120, 0.72);
}
.orbit-node.selected .node-dot span {
  background: var(--orbit-gold);
}
.orbit-node.tone-1 .node-dot {
  border-color: rgba(184, 148, 255, 0.62);
}
.orbit-node.tone-1 .node-dot span {
  background: #be9cff;
}
.orbit-node.tone-2 .node-dot {
  border-color: rgba(109, 213, 203, 0.62);
}
.orbit-node.tone-2 .node-dot span {
  background: #6dd5cb;
}
.orbit-list {
  display: none;
}
.visual-footnote {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 4px 1px;
  color: var(--orbit-faint);
  font-size: 11px;
}
.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: #6dd5cb;
  box-shadow: 0 0 9px rgba(109, 213, 203, 0.75);
}
.detail-rail {
  position: sticky;
  top: 84px;
  min-height: 390px;
  padding: 24px;
  border-radius: 21px;
}
.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.detail-heading > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.detail-number {
  color: rgba(246, 201, 120, 0.36);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 1;
}
.surprise-button {
  gap: 5px;
  padding: 4px 0;
  font-size: 12px;
}
.surprise-button:hover,
.surprise-button:focus-visible {
  color: var(--orbit-gold);
}
.surprise-button svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.35;
}
.detail-category {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 43px;
  color: var(--orbit-blue);
  font-size: 12px;
}
.detail-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--orbit-blue);
  box-shadow: 0 0 10px rgba(140, 168, 255, 0.85);
}
.detail-rail h2 {
  margin: 14px 0 13px;
  color: var(--orbit-text);
  font-size: 1.65rem;
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1.18;
}
.detail-summary {
  min-height: 50px;
  margin: 0;
  color: var(--orbit-muted);
  font-size: 13px;
  line-height: 1.8;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 13px;
  margin-top: 23px;
  padding-top: 17px;
  border-top: 1px solid rgba(169, 183, 255, 0.12);
  color: var(--orbit-faint);
  font-size: 11px;
}
.detail-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.detail-meta svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.5;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 18px;
}
.detail-tags span {
  padding: 5px 8px;
  border-radius: 6px;
  background: rgba(140, 168, 255, 0.1);
  color: var(--orbit-blue);
  font-size: 10px;
}
.detail-link {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  padding-bottom: 7px;
  border-bottom: 1px solid rgba(246, 201, 120, 0.52);
  color: var(--orbit-gold) !important;
  font-size: 13px;
  font-weight: 600;
  transition: gap 0.2s ease, border-color 0.2s ease;
}
.detail-link:hover,
.detail-link:focus-visible {
  gap: 17px;
  border-color: var(--orbit-gold);
}
.detail-link svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}
.detail-rail > .detail-label {
  display: block;
}
.detail-rail > h2 + .detail-summary {
  margin-top: 30px;
}
.orbit-state {
  display: flex;
  min-height: 560px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9px;
  border: 1px solid rgba(169, 183, 255, 0.1);
  border-radius: 18px;
  background: radial-gradient(circle at 50% 45%, rgba(81, 101, 217, 0.16), transparent 28%), #10152f;
  color: var(--orbit-muted);
  text-align: center;
}
.orbit-state strong {
  color: var(--orbit-text);
  font-size: 14px;
  font-weight: 500;
}
.orbit-state > span:not(.state-orb):not(.state-mark) {
  color: var(--orbit-faint);
  font-size: 12px;
}
.state-orb,
.state-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  margin-bottom: 7px;
  border: 1px solid rgba(246, 201, 120, 0.4);
  border-radius: 50%;
  color: var(--orbit-gold);
  font-size: 18px;
  box-shadow: 0 0 28px rgba(246, 201, 120, 0.14);
}
.state-orb {
  background: radial-gradient(circle, var(--orbit-gold) 0 12%, transparent 14%), rgba(246, 201, 120, 0.06);
  animation: state-pulse 1.8s ease-in-out infinite;
}
.state-mark {
  border-color: rgba(140, 168, 255, 0.34);
  color: var(--orbit-blue);
}
.orbit-state button {
  margin-top: 10px;
  padding: 9px 15px;
  border: 1px solid rgba(246, 201, 120, 0.36);
  border-radius: 8px;
  background: rgba(246, 201, 120, 0.1);
  color: var(--orbit-gold);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}
.orbit-state button:hover,
.orbit-state button:focus-visible {
  background: rgba(246, 201, 120, 0.18);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
button:focus-visible,
a:focus-visible,
input:focus-visible {
  outline: 2px solid var(--orbit-gold);
  outline-offset: 3px;
}
@keyframes star-drift {
  from { opacity: 0.24; transform: scale(0.8); }
  to { opacity: 0.72; transform: scale(1.2); }
}
@keyframes state-pulse {
  0%, 100% { box-shadow: 0 0 18px rgba(246, 201, 120, 0.1); }
  50% { box-shadow: 0 0 34px rgba(246, 201, 120, 0.3); }
}
@media (prefers-reduced-motion: reduce) {
  .orbit-page *,
  .orbit-page *::before,
  .orbit-page *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
@media (max-width: 980px) {
  .orbit-content {
    grid-template-columns: minmax(0, 1fr);
  }
  .detail-rail {
    position: relative;
    top: auto;
  }
}
@media (max-width: 759px) {
  .orbit-shell {
    padding: 48px 16px 70px;
  }
  .orbit-heading-row {
    display: block;
  }
  .orbit-heading-row h1 {
    font-size: clamp(2.8rem, 16vw, 4.2rem);
  }
  .orbit-count {
    margin-top: 22px;
  }
  .orbit-toolbar {
    display: block;
    margin-top: 30px;
  }
  .orbit-search {
    max-width: none;
  }
  .orbit-sort {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
    box-sizing: border-box;
    margin-top: 10px;
    overflow: hidden;
  }
  .orbit-sort button {
    width: auto;
    min-width: 0;
    box-sizing: border-box;
    padding: 0 4px;
    overflow: hidden;
    white-space: nowrap;
  }
  .orbit-category-row {
    display: block;
  }
  .orbit-category-label {
    display: block;
    margin-bottom: 10px;
  }
  .orbit-visual {
    padding: 14px;
    border-radius: 19px;
  }
  .visual-heading {
    padding: 4px 4px 14px;
  }
  .visual-heading > div {
    display: block;
  }
  .visual-subtitle {
    display: block;
    margin-top: 4px;
  }
  .orbit-stage,
  .orbit-state {
    min-height: 300px;
  }
  .orbit-node {
    padding: 3px;
  }
  .node-copy {
    display: none;
  }
  .orbit-node .node-dot {
    width: 11px;
    height: 11px;
  }
  .orbit-node.selected .node-dot {
    width: 17px;
    height: 17px;
  }
  .orbit-list {
    display: grid;
    gap: 7px;
    margin-top: 12px;
  }
  .orbit-list-item {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    min-height: 58px;
    padding: 9px 10px;
    border: 1px solid rgba(169, 183, 255, 0.1);
    border-radius: 11px;
    background: rgba(16, 21, 47, 0.72);
    color: var(--orbit-text);
    cursor: pointer;
    font: inherit;
    text-align: left;
  }
  .orbit-list-item:hover,
  .orbit-list-item:focus-visible,
  .orbit-list-item.selected {
    border-color: rgba(246, 201, 120, 0.4);
    background: rgba(246, 201, 120, 0.08);
  }
  .list-index {
    flex: 0 0 22px;
    color: var(--orbit-gold);
    font-size: 10px;
  }
  .list-copy {
    display: flex;
    min-width: 0;
    flex: 1;
    flex-direction: column;
    gap: 3px;
  }
  .list-copy strong {
    overflow: hidden;
    font-size: 12px;
    font-weight: 500;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .list-copy small {
    color: var(--orbit-faint);
    font-size: 10px;
  }
  .orbit-list-item > svg {
    width: 16px;
    height: 16px;
    flex: 0 0 auto;
    fill: none;
    stroke: var(--orbit-faint);
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.5;
  }
  .visual-footnote {
    padding-top: 13px;
    font-size: 10px;
  }
  .detail-rail {
    min-height: 0;
    padding: 20px;
  }
  .detail-category {
    margin-top: 28px;
  }
}
</style>
