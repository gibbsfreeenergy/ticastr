# Reading Orbit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a responsive `/orbit` public page that presents real article metadata as a searchable, filterable reading constellation.

**Architecture:** Keep the new behavior isolated in `views/orbit`. A pure `orbitState.js` module will normalize API records, derive categories, filter/sort results, position nodes, and select a surprise article; `Orbit.vue` will own request lifecycle and UI state. Existing router, API, store, layout, and article reader remain the integration points.

**Tech Stack:** Vue 3 Options API, Vue Router 5, Vuetify 4, plain CSS/SVG, Vitest 4 with jsdom.

**Spec:** `docs/superpowers/specs/2026-08-31-reading-orbit-design.md`

## Global Constraints

- Use the existing Vue Options API style, double-quoted strings, and public-app directory responsibilities.
- Request only existing cursor-paged article metadata through relative `/api` paths; do not fetch Markdown or add an API endpoint.
- Do not add a dependency, global store state, schema migration, admin change, fake fallback articles, or hardcoded provider URL.
- Keep the route usable with loading, API failure, empty data, and malformed metadata.
- Use semantic controls with visible focus states and disable ambient motion under `prefers-reduced-motion: reduce`.
- Verify the public app with its test, lint, build, and interactive desktop/mobile checks.

---

### Task 1: Build the pure orbit data/state module with tests

**Files:**
- Create: `blog-vue/blog/src/views/orbit/orbitState.test.js`
- Create: `blog-vue/blog/src/views/orbit/orbitState.js`

**Interfaces:**
- Produces `normalizeArticles(items)`, `getOrbitCategories(articles)`, `filterOrbitArticles(articles, options)`, `sortOrbitArticles(articles, mode)`, `getOrbitPosition(index, count)`, and `chooseSurprise(articles, currentId)`.
- A normalized article has `{ id, title, summary, categoryId, categoryName, tags, createTime, readingMinutes, sourceIndex, searchText }`.
- `getOrbitPosition` returns `{ left, top, x, y }`, where `left`/`top` are percentage strings and `x`/`y` are numeric percentages for SVG lines.

- [ ] **Step 1: Write the failing tests**

```js
import { describe, expect, it } from "vitest";
import {
  chooseSurprise,
  filterOrbitArticles,
  getOrbitCategories,
  getOrbitPosition,
  normalizeArticles,
  sortOrbitArticles
} from "./orbitState";

const articles = normalizeArticles([
  {
    id: 1,
    articleTitle: "Redis 的第二条路",
    categoryName: "系统",
    createTime: "2026-08-30T00:00:00Z",
    tagDTOList: [{ id: 1, tagName: "性能" }],
    articleSummary: "从缓存故障里找出路",
    wordCount: 1600
  },
  {
    id: 2,
    articleTitle: "给夏天写一封信",
    categoryName: "生活",
    createTime: "2026-07-01T00:00:00Z",
    tagDTOList: [{ id: 2, tagName: "随笔" }],
    wordCount: 400
  }
]);

describe("orbitState", () => {
  it("normalizes incomplete metadata without throwing", () => {
    const [article] = normalizeArticles([null, { id: 9, articleTitle: "  标题  " }]);
    expect(article).toMatchObject({
      id: 9,
      title: "标题",
      categoryName: "未分类",
      tags: [],
      readingMinutes: 1
    });
  });

  it("derives unique categories and filters through searchable fields", () => {
    expect(getOrbitCategories(articles)).toEqual(["系统", "生活"]);
    expect(filterOrbitArticles(articles, { query: "性能" })).toHaveLength(1);
    expect(filterOrbitArticles(articles, { category: "生活" })[0].id).toBe(2);
  });

  it("sorts by date and reading duration while preserving stable ties", () => {
    expect(sortOrbitArticles(articles, "latest").map(item => item.id)).toEqual([1, 2]);
    expect(sortOrbitArticles(articles, "oldest").map(item => item.id)).toEqual([2, 1]);
    expect(sortOrbitArticles(articles, "reading").map(item => item.id)).toEqual([1, 2]);
  });

  it("returns bounded deterministic positions and a different surprise when possible", () => {
    expect(getOrbitPosition(3, 8)).toEqual(getOrbitPosition(3, 8));
    const position = getOrbitPosition(3, 8);
    expect(position.x).toBeGreaterThanOrEqual(8);
    expect(position.x).toBeLessThanOrEqual(92);
    expect(position.y).toBeGreaterThanOrEqual(12);
    expect(position.y).toBeLessThanOrEqual(88);
    expect(chooseSurprise(articles, 1).id).toBe(2);
    expect(chooseSurprise([articles[0]], 1).id).toBe(1);
  });
});
```

- [ ] **Step 2: Run the focused test to verify it fails for the missing module**

Run from `blog-vue/blog`: `npm exec vitest run src/views/orbit/orbitState.test.js`
Expected: FAIL because `./orbitState` does not exist.

- [ ] **Step 3: Implement the minimal pure module**

Implement these behaviors in `orbitState.js`:

```js
const DEFAULT_CATEGORY = "未分类";

function text(value, fallback = "") {
  const result = String(value ?? "").trim();
  return result || fallback;
}

export function normalizeArticles(items) {
  return (Array.isArray(items) ? items : []).filter(Boolean).map((item, sourceIndex) => {
    const tags = Array.isArray(item.tagDTOList)
      ? item.tagDTOList.map(tag => text(tag?.tagName)).filter(Boolean)
      : [];
    const wordCount = Number(item.wordCount ?? item.wordNum ?? 0);
    const readingMinutes = Number.isFinite(wordCount) && wordCount > 0
      ? Math.max(1, Math.round(wordCount / 400))
      : 1;
    const title = text(item.articleTitle, "未命名文章");
    const summary = text(item.articleSummary ?? item.summary ?? item.articleIntro);
    const categoryName = text(item.categoryName, DEFAULT_CATEGORY);
    return {
      id: item.id ?? item.articleId ?? `article-${sourceIndex}`,
      title,
      summary,
      categoryId: item.categoryId ?? null,
      categoryName,
      tags,
      createTime: item.createTime ?? null,
      readingMinutes,
      sourceIndex,
      searchText: [title, summary, categoryName, ...tags].join(" ").toLocaleLowerCase()
    };
  });
}

export function getOrbitCategories(articles) {
  return [...new Set((articles || []).map(article => article.categoryName).filter(Boolean))];
}

export function filterOrbitArticles(articles, { query = "", category = "全部" } = {}) {
  const needle = String(query).trim().toLocaleLowerCase();
  return (articles || []).filter(article => {
    const matchesCategory = category === "全部" || article.categoryName === category;
    return matchesCategory && (!needle || article.searchText.includes(needle));
  });
}

export function sortOrbitArticles(articles, mode = "latest") {
  return [...(articles || [])].sort((left, right) => {
    if (mode === "reading" && right.readingMinutes !== left.readingMinutes) {
      return right.readingMinutes - left.readingMinutes;
    }
    const leftTime = Date.parse(left.createTime || "") || 0;
    const rightTime = Date.parse(right.createTime || "") || 0;
    if (mode === "oldest" && leftTime !== rightTime) return leftTime - rightTime;
    if (mode !== "oldest" && leftTime !== rightTime) return rightTime - leftTime;
    return left.sourceIndex - right.sourceIndex;
  });
}

export function getOrbitPosition(index, count) {
  const safeCount = Math.max(1, count);
  const angle = (index * 137.5 - 90) * (Math.PI / 180);
  const radius = Math.min(37, 18 + safeCount * 1.3);
  const x = Math.min(92, Math.max(8, 50 + Math.cos(angle) * radius));
  const y = Math.min(88, Math.max(12, 50 + Math.sin(angle) * radius * 0.72));
  return { left: `${x}%`, top: `${y}%`, x, y };
}

export function chooseSurprise(articles, currentId) {
  const list = articles || [];
  if (list.length < 2) return list[0] || null;
  return list.find(article => article.id !== currentId) || list[0];
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run from `blog-vue/blog`: `npm exec vitest run src/views/orbit/orbitState.test.js`
Expected: 4 tests pass with 0 failures.

- [ ] **Step 5: Commit the isolated state module**

```bash
git add blog-vue/blog/src/views/orbit/orbitState.js blog-vue/blog/src/views/orbit/orbitState.test.js
git commit -m "feat: add reading orbit state helpers"
```

### Task 2: Add the public Orbit page and its interaction surface

**Files:**
- Create: `blog-vue/blog/src/views/orbit/Orbit.vue`

**Interfaces:**
- Consumes the six exports from `orbitState.js` and `this.$api.article.home`.
- Renders `articles`, `visibleArticles`, `positionedArticles`, `selectedArticle`, `categories`, `loading`, and `error` states.
- Emits no custom event; opens article routes with `this.$router.push({ path: "/articles/" + article.id })`.

- [ ] **Step 1: Write a failing page behavior test**

Create `blog-vue/blog/src/views/orbit/Orbit.test.js` with a mounted component test that supplies a `$api.article.home` stub and `$router.push` spy. Assert that the page renders API titles, filters by a category button, updates the selected detail after clicking a node, and calls the router when `打开文章` is clicked. Include a second test for a rejected request rendering `重新连接`.

- [ ] **Step 2: Run the page test to verify it fails because `Orbit.vue` is missing**

Run from `blog-vue/blog`: `npm exec vitest run src/views/orbit/Orbit.test.js`
Expected: FAIL because the component has not been created.

- [ ] **Step 3: Implement the page shell and request lifecycle**

Use a semantic `<main aria-labelledby="orbit-title">`. In `created`, call:

```js
const response = await this.$api.article.home({
  params: { size: 50 },
  suppressErrorToast: true,
  timeout: 5000
});
this.articles = normalizeArticles(response?.data?.items || []);
this.selectedId = this.articles[0]?.id ?? null;
```

Keep the shell visible while loading or on error. Use `errorMessage` as `暂时无法读取文章元数据，请稍后重试。` and expose `重新连接`; show `还没有可以观测的文章。` for a successful empty page. Use local `query`, `category`, and `sortMode` state, with `watch.visibleArticles` preserving or resetting `selectedId` to the first visible item.

- [ ] **Step 4: Implement the observatory composition and controls**

Add the three design regions from the spec:

```html
<header class="orbit-header">
  <p class="orbit-kicker">TICASTR / ORBIT</p>
  <h1 id="orbit-title">阅读星图</h1>
  <p class="orbit-lede">把写过的东西，放回同一片天空。</p>
  <label class="orbit-search">
    <span class="sr-only">搜索文章</span>
    <input v-model="query" type="search" placeholder="搜索标题、标签或分类" @keydown.esc="clearSearch" />
  </label>
  <div class="orbit-controls">
    <button v-for="option in sortOptions" :key="option.value" type="button" :class="{ active: sortMode === option.value }" @click="sortMode = option.value">{{ option.label }}</button>
  </div>
  <nav aria-label="文章分类" class="orbit-categories">
    <button type="button" :class="{ active: category === &quot;全部&quot; }" @click="category = &quot;全部&quot;">全部</button>
    <button v-for="item in categories" :key="item" type="button" :class="{ active: category === item }" @click="category = item">{{ item }}</button>
  </nav>
</header>
```

The orbit field uses inline SVG lines from the center to `positionedArticles`, while the actual node controls remain native `<button>` elements with `aria-pressed`, an accessible label, and a visible selected/focus state. Add a mobile `.orbit-list` with the same article controls so the visualization never hides the content.

- [ ] **Step 5: Implement detail rail and core interactions**

Render the selected article's category/date/tags/title/summary and a real `router-link` labeled `打开文章`. Implement `surpriseMe()` with `chooseSurprise(this.visibleArticles, this.selectedId)`, `clearSearch()`, and `openArticle(article)`. Add `换一个` as a real button in the detail rail. Format dates through the existing global `date` helper and use `readingMinutes + "分钟阅读"` for the duration label.

- [ ] **Step 6: Add scoped responsive observatory styling**

Use the spec palette (`#11152b`, `#171d3b`, `#f6c978`, `#8ca8ff`) with a bounded max-width shell, CSS grid/stars as low-contrast decoration, and no external assets. Desktop uses a two-column field/detail layout; mobile hides the positioned labels, shows the stacked list, and moves the detail block below it. Add `@media (prefers-reduced-motion: reduce)` to remove ambient drift and selection transitions.

- [ ] **Step 7: Run page tests to verify the interaction surface passes**

Run from `blog-vue/blog`: `npm exec vitest run src/views/orbit/Orbit.test.js`
Expected: all page tests pass with 0 failures.

### Task 3: Register and expose the route

**Files:**
- Modify: `blog-vue/blog/src/router/index.js`
- Modify: `blog-vue/blog/src/components/layout/TopNavBar.vue`
- Modify: `blog-vue/blog/src/components/layout/SideNavBar.vue`

- [ ] **Step 1: Add the lazy route**

Insert `{ path: "/orbit", component: () => import("../views/orbit/Orbit.vue"), meta: { title: "阅读星图" } }` alongside the other public routes.

- [ ] **Step 2: Add navigation entries**

Add a `阅读星图` router link to the existing desktop `发现` submenu and the mobile drawer list. Reuse the existing `iconfont iconfaxian` class; do not introduce a new icon dependency.

- [ ] **Step 3: Run focused lint and route/page tests**

Run from `blog-vue/blog`: `npm exec eslint src/router/index.js src/components/layout/TopNavBar.vue src/components/layout/SideNavBar.vue src/views/orbit` and `npm exec vitest run src/views/orbit`.
Expected: exit 0, with no lint errors and all orbit tests passing.

### Task 4: Verify the complete public app

**Files:**
- Modify: none unless verification reveals a defect.

- [ ] **Step 1: Run the full public test suite**

Run from `blog-vue/blog`: `npm run test:run`
Expected: all existing and new tests pass.

- [ ] **Step 2: Run the public linter and production build**

Run from `blog-vue/blog`: `npm run lint` and `npm run build`
Expected: both commands exit 0.

- [ ] **Step 3: Run the dev server for rendered verification**

Run from `blog-vue/blog`: `npm run dev -- --host 127.0.0.1` and open `/orbit` in the available browser. Inspect a desktop viewport and a mobile-sized viewport. Verify the metadata loading shell, category filter, search, node selection, `换一个`, article link, empty state, error/retry state, visible keyboard focus, and no horizontal overflow.

- [ ] **Step 4: Review the final diff and commit the feature**

Run `git diff --check`, `git diff --stat`, and `git status --short`; confirm only the spec/plan and Reading Orbit files changed, with no secrets, build output, or dependency lockfile churn. Commit the implementation with `git add` of the route, navigation, page, helpers, and tests followed by `git commit -m "feat: add reading orbit discovery page"`.
