<template>
  <aside class="article-sidebar">
    <section class="sidebar-block toc-block">
      <div class="sidebar-heading"><span class="sidebar-symbol">⌘</span><span>目录</span></div>
      <div id="toc" aria-label="文章目录" />
    </section>

    <section class="sidebar-block latest-block">
      <div class="sidebar-heading"><span class="sidebar-symbol">✧</span><span>最新文章</span></div>
      <div class="article-list">
        <router-link v-for="item of article.newestArticleList" :key="item.id" :to="'/articles/' + item.id" class="article-item">
          <img :src="item.articleCover" :alt="item.articleTitle" width="56" height="56" loading="lazy" decoding="async" />
          <span>
            <strong>{{ item.articleTitle }}</strong>
            <small>{{ date(item.createTime) }}</small>
          </span>
        </router-link>
      </div>
    </section>

    <div class="sidebar-note">
      <span>✦</span>
      <p>慢一点，也很好。</p>
      <small>愿你在文字里找到一点自己的节奏。</small>
    </div>
  </aside>
</template>

<script>
export default {
  name: "ArticleSidebar",
  props: { article: { type: Object, required: true } }
};
</script>

<style scoped>
.article-sidebar {
  position: sticky;
  top: 104px;
  min-width: 0;
}

.sidebar-block {
  padding-bottom: 28px;
  border-bottom: 1px solid var(--line);
}

.latest-block {
  margin-top: 30px;
}

.sidebar-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 17px;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 17px;
}

.sidebar-symbol {
  color: var(--sage-deep);
  font-size: 19px;
}

:deep(#toc) {
  color: var(--muted);
}

:deep(.toc-list) {
  margin: 0;
  padding: 0;
  list-style: none;
}

:deep(.toc-list-item) {
  margin: 0;
  padding: 0;
  list-style: none;
}

:deep(.toc-link) {
  display: block;
  padding: 6px 0 6px 14px;
  border-left: 1px solid transparent;
  color: var(--muted) !important;
  font-size: 12px;
  line-height: 1.55;
  transition: color 180ms ease, border-color 180ms ease, padding 180ms ease;
}

:deep(.toc-link:hover),
:deep(.is-active-link) {
  border-left-color: var(--sage);
  color: var(--sage-deep) !important;
}

.article-list {
  display: grid;
  gap: 13px;
}

.article-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 11px;
  align-items: center;
  min-width: 0;
}

.article-item img {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  transition: transform 300ms ease;
}

.article-item:hover img {
  transform: scale(1.05) rotate(-2deg);
}

.article-item > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.article-item strong {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 180ms ease;
}

.article-item:hover strong {
  color: var(--sage-deep);
}

.article-item small {
  color: var(--muted-light);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 11px;
}

.sidebar-note {
  margin-top: 28px;
  padding: 19px 20px;
  border-radius: var(--radius-md);
  background: rgba(229, 194, 199, 0.16);
}

.sidebar-note > span {
  color: var(--gold);
}

.sidebar-note p {
  margin: 10px 0 4px !important;
  color: var(--sage-deep);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18px;
}

.sidebar-note small {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .article-sidebar {
    position: static;
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 30px;
    margin-top: 70px;
  }

  .sidebar-block {
    padding-bottom: 0;
    border-bottom: 0;
  }

  .latest-block {
    margin-top: 0;
  }

  .sidebar-note {
    grid-column: 1 / -1;
    margin-top: 0;
  }
}

@media (max-width: 480px) {
  .article-sidebar {
    display: block;
  }

  .latest-block {
    margin-top: 32px;
  }

  .sidebar-note {
    margin-top: 28px;
  }
}
</style>
