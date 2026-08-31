<template>
  <div class="home-page">
    <section class="home-intro">
      <div>
        <p class="home-eyebrow">工作台 <span>/</span> OVERVIEW</p>
        <h1>早上好，管理员</h1>
        <p class="home-subtitle">这里是你的博客概览，今天也从一件小事开始。</p>
      </div>
      <el-button type="primary" class="publish-button" @click="$router.push({ path: '/articles' })">
        <AppIcon name="pen" :size="16" />
        <span>发布文章</span>
      </el-button>
    </section>

    <section class="metric-grid" aria-label="数据概览">
      <button
        v-for="metric in metrics"
        :key="metric.label"
        class="metric-card"
        :class="`metric-card--${metric.theme}`"
        type="button"
        :aria-label="`打开${metric.label}`"
        @click="$router.push({ path: metric.path })"
      >
        <span class="metric-icon"><AppIcon :name="metric.icon" :size="19" /></span>
        <span class="metric-copy">
          <span class="metric-label">{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <span class="metric-caption">{{ metric.caption }}</span>
        </span>
        <AppIcon class="metric-arrow" name="chevronRight" :size="16" />
      </button>
    </section>

    <section class="dashboard-grid dashboard-grid--primary">
      <article class="dashboard-panel dashboard-panel--wide">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">TRAFFIC</p>
            <h2>一周访问量</h2>
          </div>
          <span class="panel-status" :class="{ 'is-loading': loading }">
            <span class="status-dot" />
            {{ loading ? "加载中" : "实时数据" }}
          </span>
        </header>
        <div class="chart-frame chart-frame--line" v-loading="loading">
          <v-chart class="dashboard-chart" :option="viewCount" autoresize />
          <p v-if="!loading && viewCount.xAxis.data.length === 0" class="chart-empty">暂无访问记录</p>
          <p v-if="requestError" class="chart-note">数据暂时不可用，页面其余功能仍可正常使用。</p>
        </div>
      </article>

      <article class="dashboard-panel dashboard-panel--heatmap">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">CONSISTENCY</p>
            <h2>文章贡献统计</h2>
          </div>
          <span class="panel-meta">过去一年</span>
        </header>
        <div class="heatmap-frame" v-loading="loading">
          <calendar-heatmap :end-date="new Date()" :values="articleStatisticsList" />
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--secondary">
      <article class="dashboard-panel dashboard-panel--ranking">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">TOP CONTENT</p>
            <h2>文章浏览量排行</h2>
          </div>
          <span class="panel-meta">热门文章</span>
        </header>
        <div class="chart-frame chart-frame--ranking" v-loading="loading">
          <v-chart class="dashboard-chart" :option="ariticleRank" autoresize />
          <p v-if="!loading && ariticleRank.xAxis.data.length === 0" class="chart-empty">暂无文章排行</p>
        </div>
      </article>

      <article class="dashboard-panel dashboard-panel--category">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">CATEGORIES</p>
            <h2>文章分类统计</h2>
          </div>
          <span class="panel-meta">内容分布</span>
        </header>
        <div class="chart-frame chart-frame--category" v-loading="loading">
          <v-chart class="dashboard-chart" :option="category" autoresize />
          <p v-if="!loading && category.series[0].data.length === 0" class="chart-empty">暂无分类数据</p>
        </div>
      </article>
    </section>

    <section class="dashboard-grid dashboard-grid--secondary">
      <article class="dashboard-panel dashboard-panel--map">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">AUDIENCE</p>
            <h2>用户地域分布</h2>
          </div>
          <el-radio-group v-model="type" class="audience-toggle" size="small">
            <el-radio :value="1">用户</el-radio>
            <el-radio :value="2">游客</el-radio>
          </el-radio-group>
        </header>
        <div class="chart-frame chart-frame--map" v-loading="loading">
          <home-map-chart class="dashboard-chart" :option="userAreaMap" autoresize />
        </div>
      </article>

      <article class="dashboard-panel dashboard-panel--tags">
        <header class="panel-heading">
          <div>
            <p class="panel-kicker">TAXONOMY</p>
            <h2>文章标签统计</h2>
          </div>
          <span class="panel-meta">{{ tagDTOList.length }} 个标签</span>
        </header>
        <div class="tag-frame" v-loading="loading">
          <tag-cloud :data="tagDTOList" />
          <p v-if="!loading && tagDTOList.length === 0" class="chart-note">还没有标签数据</p>
        </div>
      </article>
    </section>
  </div>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";
import CalendarHeatmap from "../../components/CalendarHeatmap.vue";
import TagCloud from "../../components/TagCloud.vue";
import { defineAsyncComponent } from "vue";

const HomeChart = defineAsyncComponent(() => import("../../components/charts/HomeChart.vue"));
const HomeMapChart = defineAsyncComponent(() => import("../../components/charts/HomeMapChart.vue"));

export default {
  name: "HomeView",
  components: { AppIcon, CalendarHeatmap, TagCloud, "v-chart": HomeChart, "home-map-chart": HomeMapChart },
  created() {
    this.listUserArea();
    this.getData();
  },
  data() {
    return {
      loading: true,
      requestError: false,
      type: 1,
      viewsCount: 0,
      messageCount: 0,
      userCount: 0,
      articleCount: 0,
      articleStatisticsList: [],
      tagDTOList: [],
      viewCount: {
        animationDuration: 500,
        tooltip: {
          trigger: "axis",
          axisPointer: { type: "line" }
        },
        color: ["#0071e3"],
        grid: {
          left: 8,
          right: 12,
          bottom: 10,
          top: 30,
          containLabel: true
        },
        xAxis: {
          type: "category",
          boundaryGap: false,
          data: [],
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: "#86868b", fontSize: 11 }
        },
        yAxis: {
          type: "value",
          minInterval: 1,
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: "#86868b", fontSize: 11 },
          splitLine: { lineStyle: { color: "#eeeef2", type: "dashed" } }
        },
        series: [
          {
            name: "访问量",
            type: "line",
            data: [],
            smooth: true,
            symbol: "circle",
            symbolSize: 7,
            lineStyle: { width: 3, color: "#0071e3" },
            itemStyle: { color: "#0071e3", borderColor: "#fff", borderWidth: 2 },
            areaStyle: { color: "rgba(0, 113, 227, 0.08)" }
          }
        ]
      },
      ariticleRank: {
        animationDuration: 500,
        tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
        color: ["#0071e3"],
        grid: {
          left: 8,
          right: 12,
          bottom: 10,
          top: 16,
          containLabel: true
        },
        xAxis: {
          type: "category",
          data: [],
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: "#86868b", fontSize: 10, rotate: 18 }
        },
        yAxis: {
          type: "value",
          minInterval: 1,
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: "#86868b", fontSize: 11 },
          splitLine: { lineStyle: { color: "#eeeef2", type: "dashed" } }
        },
        series: [
          {
            name: "浏览量",
            type: "bar",
            barMaxWidth: 28,
            data: [],
            itemStyle: { color: "#0071e3", borderRadius: [7, 7, 2, 2] }
          }
        ]
      },
      category: {
        color: ["#0071e3", "#34c759", "#ff9f0a", "#af52de", "#5ac8fa", "#ff6482"],
        legend: {
          data: [],
          bottom: 0,
          left: "center",
          icon: "circle",
          textStyle: { color: "#6e6e73", fontSize: 10 }
        },
        tooltip: { trigger: "item" },
        series: [
          {
            name: "文章分类",
            type: "pie",
            radius: ["38%", "68%"],
            center: ["50%", "45%"],
            label: { show: false },
            data: []
          }
        ]
      },
      userAreaMap: {
        tooltip: {
          formatter(event) {
            const value = event.value || 0;
            return `${event.seriesName}<br />${event.name}：${value}`;
          }
        },
        visualMap: {
          min: 0,
          max: 1000,
          right: 8,
          bottom: 12,
          showLabel: true,
          pieces: [
            { gt: 100, label: "100人以上", color: "#0071e3" },
            { gte: 51, lte: 100, label: "51-100人", color: "#5ac8fa" },
            { gte: 21, lte: 50, label: "21-50人", color: "#34c759" },
            { gt: 0, lte: 20, label: "1-20人", color: "#b9d9ff" }
          ],
          show: true,
          textStyle: { color: "#6e6e73", fontSize: 10 }
        },
        geo: {
          map: "china",
          zoom: 1.12,
          layoutCenter: ["45%", "48%"],
          itemStyle: {
            areaColor: "#f1f6fc",
            borderColor: "#c9d9eb",
            borderWidth: 1
          },
          emphasis: {
            itemStyle: { areaColor: "#dcecff", shadowBlur: 0 }
          }
        },
        series: [
          {
            name: "用户人数",
            type: "map",
            geoIndex: 0,
            data: [],
            itemStyle: { areaColor: "#b9d9ff" }
          }
        ]
      }
    };
  },
  computed: {
    metrics() {
      return [
        { label: "访问量", value: this.viewsCount, caption: "总访问次数", icon: "eye", theme: "blue", path: "/" },
        { label: "用户量", value: this.userCount, caption: "注册用户", icon: "users", theme: "green", path: "/users" },
        { label: "文章量", value: this.articleCount, caption: "已发布内容", icon: "file", theme: "orange", path: "/articles" },
        { label: "留言量", value: this.messageCount, caption: "待处理互动", icon: "message", theme: "purple", path: "/messages" }
      ];
    }
  },
  methods: {
    getData() {
      this.$api.admin.home().then(data => {
        const result = data.data || {};
        this.viewsCount = result.viewsCount || 0;
        this.messageCount = result.messageCount || 0;
        this.userCount = result.userCount || 0;
        this.articleCount = result.articleCount || 0;
        this.articleStatisticsList = result.articleStatisticsList || [];
        this.viewCount.xAxis.data = [];
        this.viewCount.series[0].data = [];
        this.category.series[0].data = [];
        this.category.legend.data = [];
        this.ariticleRank.series[0].data = [];
        this.ariticleRank.xAxis.data = [];
        this.tagDTOList = [];

        (result.uniqueViewDTOList || []).forEach(item => {
          this.viewCount.xAxis.data.push(item.day);
          this.viewCount.series[0].data.push(item.viewsCount || 0);
        });
        (result.categoryDTOList || []).forEach(item => {
          this.category.series[0].data.push({ value: item.articleCount || 0, name: item.categoryName });
          this.category.legend.data.push(item.categoryName);
        });
        (result.articleRankDTOList || []).forEach(item => {
          this.ariticleRank.series[0].data.push(item.viewsCount || 0);
          this.ariticleRank.xAxis.data.push(item.articleTitle);
        });
        (result.tagDTOList || []).forEach(item => {
          this.tagDTOList.push({ id: item.id, name: item.tagName, tagName: item.tagName });
        });
      }).catch(() => {
        this.requestError = true;
      }).finally(() => {
        this.loading = false;
      });
    },
    listUserArea() {
      this.$api.admin.userAreas({ params: { type: this.type } }).then(data => {
        this.userAreaMap.series[0].data = data.data || [];
      }).catch(() => {
        this.requestError = true;
      });
    }
  },
  watch: {
    type() {
      this.listUserArea();
    }
  }
};
</script>

<style scoped>
.home-page {
  max-width: 1500px;
  margin: 0 auto;
}

.home-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.home-eyebrow,
.panel-kicker {
  margin: 0 0 8px;
  color: var(--admin-blue);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.12em;
  line-height: 1.2;
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
  line-height: 1.1;
}

.home-subtitle {
  margin: 10px 0 0;
  color: var(--admin-text-secondary);
  font-size: 14px;
}

.publish-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 10px 17px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-card {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 13px;
  padding: 20px;
  color: var(--admin-text);
  text-align: left;
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 16px;
  box-shadow: var(--admin-shadow);
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.metric-card:hover {
  border-color: rgba(0, 113, 227, 0.24);
  box-shadow: 0 16px 42px rgba(29, 29, 31, 0.1);
  transform: translateY(-2px);
}

.metric-card:active {
  transform: translateY(0) scale(0.99);
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

.metric-card--green .metric-icon {
  color: #218c3a;
  background: #effbf2;
}

.metric-card--orange .metric-icon {
  color: #a96500;
  background: #fff8ea;
}

.metric-card--purple .metric-icon {
  color: #8d3bb1;
  background: #f7edfb;
}

.metric-copy {
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
  color: var(--admin-text);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1.15;
}

.metric-caption {
  margin-top: 3px;
  color: var(--admin-text-tertiary);
  font-size: 10px;
}

.metric-arrow {
  align-self: center;
  color: var(--admin-text-tertiary);
}

.dashboard-grid {
  display: grid;
  gap: 18px;
  margin-bottom: 18px;
}

.dashboard-grid--primary {
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
}

.dashboard-grid--secondary {
  grid-template-columns: minmax(0, 1.2fr) minmax(300px, 0.8fr);
}

.dashboard-panel {
  min-width: 0;
  padding: 22px 24px 20px;
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: 18px;
  box-shadow: var(--admin-shadow);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 42px;
  gap: 16px;
}

.panel-heading h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.panel-kicker {
  margin-bottom: 7px;
  color: var(--admin-text-tertiary);
  font-size: 9px;
}

.panel-status,
.panel-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding-top: 3px;
  color: var(--admin-text-tertiary);
  font-size: 10px;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--admin-green);
  border-radius: 50%;
}

.panel-status.is-loading .status-dot {
  background: var(--admin-orange);
  animation: status-pulse 1.4s ease-in-out infinite;
}

.chart-frame {
  position: relative;
  width: 100%;
  height: 255px;
  margin-top: 12px;
}

.chart-frame--line {
  height: 250px;
}

.chart-frame--ranking {
  height: 260px;
}

.chart-frame--category {
  height: 260px;
}

.chart-frame--map {
  height: 285px;
  margin-top: 4px;
}

.dashboard-chart,
.echarts {
  width: 100%;
  height: 100%;
}

.chart-note {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  margin: 0;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  text-align: center;
}

.chart-empty {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  margin: 0;
  color: var(--admin-text-tertiary);
  font-size: 12px;
  text-align: center;
  transform: translateY(-50%);
  pointer-events: none;
}

.heatmap-frame {
  min-height: 250px;
  padding-top: 28px;
}

.heatmap-frame :deep(.calendar-heatmap) {
  gap: 5px;
  grid-template-rows: repeat(7, 11px);
  padding: 10px 0;
}

.heatmap-frame :deep(.calendar-heatmap__day) {
  width: 11px;
  height: 11px;
  background: #eef0f3;
  border-radius: 3px;
}

.heatmap-frame :deep(.calendar-heatmap__day--1) {
  background: #cfe4ff;
}

.heatmap-frame :deep(.calendar-heatmap__day--2) {
  background: #8fc3ff;
}

.heatmap-frame :deep(.calendar-heatmap__day--3) {
  background: #4098f5;
}

.heatmap-frame :deep(.calendar-heatmap__day--4) {
  background: #0071e3;
}

.audience-toggle {
  display: inline-flex;
  padding: 3px;
  background: #f7f7fa;
  border-radius: 8px;
}

.audience-toggle :deep(.el-radio) {
  height: 26px;
  padding: 0 8px;
  margin-right: 0;
  color: var(--admin-text-tertiary);
  line-height: 26px;
  border-radius: 6px;
}

.audience-toggle :deep(.el-radio__input) {
  display: none;
}

.audience-toggle :deep(.el-radio__label) {
  padding-left: 0;
  color: inherit;
  font-size: 11px;
}

.audience-toggle :deep(.el-radio.is-checked) {
  color: var(--admin-blue);
  background: #fff;
  box-shadow: 0 2px 8px rgba(29, 29, 31, 0.08);
}

.tag-frame {
  position: relative;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tag-frame :deep(.tag-cloud) {
  gap: 10px;
  width: 100%;
  min-height: 240px;
  padding: 20px 6px;
}

.tag-frame :deep(.tag-cloud__tag) {
  padding: 6px 11px;
  background: #f5f7fa;
  border: 1px solid #edf0f4;
  border-radius: 999px;
}

@keyframes status-pulse {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid--primary,
  .dashboard-grid--secondary {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 680px) {
  .home-intro {
    align-items: flex-start;
    flex-direction: column;
    margin-bottom: 22px;
  }

  .home-intro h1 {
    font-size: 32px;
  }

  .publish-button {
    width: 100%;
    justify-content: center;
  }

  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-panel {
    padding: 20px 16px 16px;
    border-radius: 16px;
  }

  .panel-heading {
    gap: 8px;
  }

  .panel-heading h2 {
    font-size: 16px;
  }

  .panel-meta {
    max-width: 90px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .chart-frame--map {
    height: 245px;
  }

  .heatmap-frame {
    min-height: 190px;
    padding-top: 12px;
  }

  .heatmap-frame :deep(.calendar-heatmap) {
    gap: 3px;
  }

  .heatmap-frame :deep(.calendar-heatmap__day) {
    width: 9px;
    height: 9px;
  }

  .audience-toggle {
    transform: scale(0.92);
    transform-origin: right top;
  }
}

@media (prefers-reduced-motion: reduce) {
  .metric-card,
  .panel-status .status-dot {
    animation: none;
    transition: none;
  }
}
</style>
