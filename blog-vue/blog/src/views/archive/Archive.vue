<template>
  <div class="archive-page">
    <section class="page-hero" :style="coverStyle">
      <div class="page-hero-fade" aria-hidden="true" />
      <div class="page-width page-hero-content fade-up">
        <span class="page-hero-index">02 / 03</span>
        <h1>归档</h1>
        <p>把走过的日子，轻轻收好。</p>
      </div>
    </section>

    <main class="archive-main page-width">
      <div class="archive-heading">
        <div>
          <span class="archive-kicker">THE JOURNAL</span>
          <h2>文章归档</h2>
        </div>
        <span class="archive-count">共 {{ archiveList.length }} 篇</span>
      </div>

      <div v-if="archiveList.length" class="archive-timeline" aria-label="文章归档">
        <section v-for="group in archiveGroups" :key="group.year" class="archive-year">
          <h3>{{ group.year }}</h3>
          <div class="archive-rows">
            <router-link
              v-for="item in group.items"
              :key="item.id"
              class="archive-row"
              :to="'/articles/' + item.id"
            >
              <time>{{ date(item.createTime).slice(5) }}</time>
              <span class="archive-title">{{ item.articleTitle }}</span>
              <span class="archive-arrow" aria-hidden="true">↗</span>
            </router-link>
          </div>
        </section>
      </div>

      <div v-else-if="!loading" class="archive-empty">
        <span>✦</span>
        <p>还没有可归档的文章。</p>
      </div>

      <div class="archive-load-more" v-if="!archivesComplete">
        <button class="load-more-button" type="button" :disabled="loading" @click="loadMoreArchives">
          <span>{{ loading ? "正在加载" : "加载更多" }}</span>
          <i aria-hidden="true">→</i>
        </button>
      </div>
    </main>
  </div>
</template>

<script>
export default {
  name: "ArchivePage",
  created() {
    this.listArchives();
  },
  data() {
    return {
      archiveList: [],
      nextCursor: null,
      loading: false,
      archivesComplete: false
    };
  },
  methods: {
    async listArchives() {
      if (this.loading || this.archivesComplete) return;
      this.loading = true;
      try {
        const response = await this.$api.article.archives({
          params: { cursor: this.nextCursor || undefined, size: 20 }
        });
        const page = response.data || {};
        const items = Array.isArray(page.items) ? page.items : [];
        this.archiveList.push(...items);
        this.nextCursor = page.nextCursor || null;
        this.archivesComplete = items.length === 0 || !page.hasNext;
      } finally {
        this.loading = false;
      }
    },
    loadMoreArchives() {
      return this.listArchives();
    }
  },
  computed: {
    archiveGroups() {
      const groups = new Map();
      this.archiveList.forEach(item => {
        const year = String(new Date(item.createTime).getFullYear());
        if (!groups.has(year)) groups.set(year, []);
        groups.get(year).push(item);
      });
      return [...groups.entries()].map(([year, items]) => ({ year, items }));
    },
    coverStyle() {
      const page = (this.$store.state.blogInfo.pageList || []).find(item => item.pageLabel === "archive");
      const pageCover = typeof page?.pageCover === "string" ? page.pageCover.trim() : "";
      return pageCover ? { backgroundImage: `url("${pageCover}")` } : {};
    }
  }
};
</script>

<style scoped>
.archive-main {
  padding-top: 78px;
  padding-bottom: 110px;
}

.archive-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 28px;
  border-bottom: 1px solid var(--line);
}

.archive-kicker {
  color: var(--sage-deep);
  font-size: 10px;
  letter-spacing: 0.2em;
}

.archive-heading h2 {
  margin: 13px 0 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 500;
  letter-spacing: -0.04em;
}

.archive-count {
  color: var(--muted);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 14px;
}

.archive-timeline {
  position: relative;
  padding: 18px 0 20px 76px;
}

.archive-timeline::before {
  position: absolute;
  top: 32px;
  bottom: 12px;
  left: 19px;
  width: 1px;
  background: var(--line-strong);
  content: "";
}

.archive-year {
  position: relative;
  padding: 30px 0 15px;
}

.archive-year::before {
  position: absolute;
  top: 41px;
  left: -61px;
  width: 9px;
  height: 9px;
  border: 3px solid var(--paper);
  border-radius: 50%;
  background: var(--sage);
  box-shadow: 0 0 0 1px var(--sage);
  content: "";
}

.archive-year h3 {
  position: absolute;
  top: 28px;
  left: -76px;
  margin: 0;
  color: var(--sage-deep);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 20px;
  font-weight: 500;
  transform: translateX(-100%);
}

.archive-rows {
  border-top: 1px solid var(--line);
}

.archive-row {
  display: grid;
  grid-template-columns: 94px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 24px;
  min-height: 66px;
  border-bottom: 1px dashed var(--line);
  color: var(--ink-soft);
  transition: color 180ms ease, padding 180ms ease;
}

.archive-row:hover {
  padding-left: 8px;
  color: var(--sage-deep);
}

.archive-row time {
  color: var(--muted);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 13px;
}

.archive-title {
  overflow: hidden;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archive-arrow {
  color: var(--muted-light);
  font-size: 18px;
  text-align: right;
  transition: color 180ms ease, transform 180ms ease;
}

.archive-row:hover .archive-arrow {
  color: var(--sage-deep);
  transform: translate(3px, -3px);
}

.archive-load-more {
  display: flex;
  justify-content: center;
  padding: 36px 0 0;
}

.archive-empty {
  padding: 80px 20px;
  color: var(--muted);
  text-align: center;
}

.archive-empty span {
  color: var(--gold);
  font-size: 26px;
}

.archive-empty p {
  margin: 12px 0 0 !important;
}

@media (max-width: 760px) {
  .archive-main {
    padding-top: 54px;
    padding-bottom: 78px;
  }

  .archive-heading {
    display: block;
  }

  .archive-count {
    display: block;
    margin-top: 14px;
  }

  .archive-timeline {
    padding-left: 0;
  }

  .archive-timeline::before,
  .archive-year::before {
    display: none;
  }

  .archive-year {
    padding-top: 38px;
  }

  .archive-year h3 {
    position: static;
    margin-bottom: 13px;
    transform: none;
  }

  .archive-row {
    grid-template-columns: 66px minmax(0, 1fr) 18px;
    gap: 12px;
    min-height: 62px;
  }

  .archive-title {
    font-size: 15px;
  }
}
</style>
