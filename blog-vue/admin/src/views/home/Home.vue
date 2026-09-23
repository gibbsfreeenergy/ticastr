<template>
  <div class="home-page">
    <section class="home-intro">
      <div>
        <p class="home-eyebrow">工作台 <span>/</span> BLOG</p>
        <h1>博客概览</h1>
        <p class="home-subtitle">管理文章、页面内容和网站基础信息。</p>
      </div>
    </section>

    <section class="metric-grid" aria-label="博客概览">
      <button class="metric-card" type="button" @click="$router.push({ path: '/articles' })">
        <span class="metric-icon"><AppIcon name="file" :size="19" /></span>
        <span class="metric-copy">
          <span class="metric-label">文章</span>
          <strong>{{ articleCount }}</strong>
          <span class="metric-caption">管理已发布内容</span>
        </span>
        <AppIcon class="metric-arrow" name="chevronRight" :size="16" />
      </button>
      <button class="metric-card" type="button" @click="$router.push({ path: '/pages' })">
        <span class="metric-icon"><AppIcon name="page" :size="19" /></span>
        <span class="metric-copy">
          <span class="metric-label">页面</span>
          <strong>{{ pageCount }}</strong>
          <span class="metric-caption">首页、归档和关于</span>
        </span>
        <AppIcon class="metric-arrow" name="chevronRight" :size="16" />
      </button>
    </section>

    <section class="quick-grid" aria-label="常用入口">
      <button class="quick-card" type="button" @click="$router.push({ path: '/website' })">
        <AppIcon name="globe" :size="20" />
        <span><strong>网站设置</strong><small>名称、介绍、公告和社交链接</small></span>
        <AppIcon name="chevronRight" :size="16" />
      </button>
      <button class="quick-card" type="button" @click="$router.push({ path: '/about' })">
        <AppIcon name="info" :size="20" />
        <span><strong>关于我</strong><small>编辑关于页面的 Markdown 内容</small></span>
        <AppIcon name="chevronRight" :size="16" />
      </button>
      <button class="quick-card" type="button" @click="$router.push({ path: '/traffic' })">
        <AppIcon name="activity" :size="20" />
        <span><strong>代理监控</strong><small>查看连接、来源 IP 和目标域名</small></span>
        <AppIcon name="chevronRight" :size="16" />
      </button>
    </section>

    <p v-if="error" class="home-error" role="alert">{{ error }}</p>
  </div>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";

export default {
  name: "HomeView",
  components: { AppIcon },
  data() {
    return {
      articleCount: 0,
      pageCount: 0,
      error: ""
    };
  },
  created() {
    this.loadOverview();
  },
  methods: {
    async loadOverview() {
      try {
        const [overview, pages] = await Promise.all([
          this.$api.admin.home(),
          this.$api.admin.pages()
        ]);
        this.articleCount = overview.data?.articleCount || 0;
        this.pageCount = Array.isArray(pages.data) ? pages.data.length : 0;
      } catch {
        this.error = "概览数据暂时无法读取，请稍后重试";
      }
    }
  }
};
</script>

<style scoped>
.home-page {
  max-width: 1120px;
  margin: 0 auto;
}

.home-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.home-eyebrow {
  margin: 0 0 8px;
  color: var(--admin-blue);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.home-eyebrow span {
  margin: 0 4px;
  color: var(--admin-text-tertiary);
}

.home-intro h1 {
  margin: 0;
  color: var(--admin-text);
  font-size: clamp(30px, 4vw, 42px);
  font-weight: 750;
  letter-spacing: -0.04em;
}

.home-subtitle {
  margin: 10px 0 0;
  color: var(--admin-text-secondary);
  font-size: 14px;
}

.metric-grid,
.quick-grid {
  display: grid;
  gap: 14px;
}

.metric-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 18px;
}

.metric-card,
.quick-card {
  display: flex;
  align-items: center;
  gap: 13px;
  color: var(--admin-text);
  text-align: left;
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 16px;
  box-shadow: var(--admin-shadow);
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.metric-card {
  padding: 20px;
}

.quick-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.quick-card {
  min-width: 0;
  padding: 20px;
}

.metric-card:hover,
.quick-card:hover {
  border-color: rgba(0, 113, 227, 0.24);
  box-shadow: 0 16px 42px rgba(29, 29, 31, 0.1);
  transform: translateY(-2px);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  color: var(--admin-blue);
  background: var(--admin-blue-soft);
  border-radius: 11px;
}

.metric-copy,
.quick-card > span:nth-child(2) {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.metric-label {
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.metric-copy strong {
  margin-top: 2px;
  font-size: 26px;
}

.metric-caption,
.quick-card small {
  margin-top: 4px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.quick-card > .app-icon {
  flex: 0 0 auto;
  color: var(--admin-blue);
}

.quick-card strong {
  font-size: 14px;
}

.metric-arrow {
  flex: 0 0 auto;
  color: var(--admin-text-tertiary);
}

.home-error {
  margin-top: 18px;
  color: var(--admin-red, #d70015);
  font-size: 13px;
}

@media (max-width: 820px) {
  .home-intro {
    align-items: flex-start;
    flex-direction: column;
  }

  .quick-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 520px) {
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
