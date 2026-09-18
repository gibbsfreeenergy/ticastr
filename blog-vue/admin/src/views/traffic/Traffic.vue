<template>
  <div class="traffic-page">
    <header class="traffic-header">
      <div>
        <p class="traffic-eyebrow">NETWORK <span>/</span> XRAY</p>
        <h1>代理监控</h1>
        <p class="traffic-subtitle">查看 Xray 连接、来源与目标域名的实时状态。</p>
      </div>

      <div class="traffic-toolbar" aria-label="监控控制">
        <div class="traffic-status" :class="`is-${statusTone}`">
          <span class="status-dot" aria-hidden="true" />
          <span>{{ statusLabel }}</span>
        </div>
        <label class="range-control">
          <span class="sr-only">统计范围</span>
          <select v-model="rangeKey" aria-label="统计范围">
            <option v-for="option in rangeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <button class="refresh-button" type="button" :disabled="loading" @click="loadDashboard">
          <AppIcon name="refresh" :size="15" :class="{ 'is-spinning': loading }" />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <p v-if="error" class="traffic-error" role="alert">
      <AppIcon name="alert" :size="16" />
      <span>{{ error }}</span>
      <button type="button" @click="loadDashboard">重试</button>
    </p>

    <section class="traffic-metrics" aria-label="代理监控指标">
      <article v-for="metric in metricCards" :key="metric.label" class="traffic-metric-card">
        <span class="metric-icon" :class="`is-${metric.tone}`">
          <AppIcon :name="metric.icon" :size="19" />
        </span>
        <div class="metric-copy">
          <span class="metric-label">{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <span class="metric-caption">{{ metric.caption }}</span>
        </div>
      </article>
    </section>

    <section class="traffic-main-grid">
      <article class="traffic-panel trend-panel">
        <div class="panel-header">
          <div>
            <h2>连接趋势</h2>
            <p>{{ rangeLabel }} · 按小时聚合</p>
          </div>
          <div class="metric-switch" role="tablist" aria-label="趋势指标">
            <button
              v-for="option in trendMetricOptions"
              :key="option.value"
              type="button"
              :class="{ 'is-active': trendMetric === option.value }"
              role="tab"
              :aria-selected="trendMetric === option.value"
              @click="trendMetric = option.value"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div v-if="trend.length" class="trend-chart-wrap">
          <svg
            class="trend-chart"
            viewBox="0 0 760 248"
            role="img"
            :aria-label="`${trendMetricLabel}趋势图`"
          >
            <defs>
              <linearGradient id="traffic-trend-fill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stop-color="#0071e3" stop-opacity="0.2" />
                <stop offset="1" stop-color="#0071e3" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line
              v-for="line in chartGrid"
              :key="line.label"
              x1="42"
              :y1="line.y"
              x2="744"
              :y2="line.y"
              class="chart-grid-line"
            />
            <text
              v-for="line in chartGrid"
              :key="`label-${line.label}`"
              x="34"
              :y="line.y + 4"
              class="chart-axis-label"
              text-anchor="end"
            >
              {{ line.label }}
            </text>
            <path :d="trendAreaPath" class="trend-area" />
            <polyline :points="trendPolyline" class="trend-line" />
            <circle
              v-for="point in chartPoints"
              :key="point.key"
              :cx="point.x"
              :cy="point.y"
              r="3.5"
              class="trend-point"
            />
            <text
              v-for="label in chartLabels"
              :key="label.key"
              :x="label.x"
              y="240"
              class="chart-axis-label"
              :text-anchor="label.anchor"
            >
              {{ label.text }}
            </text>
          </svg>
          <div class="trend-summary">
            <span>峰值 {{ formatNumber(trendPeak) }}</span>
            <span>最近更新 {{ updatedLabel }}</span>
          </div>
        </div>
        <div v-else class="panel-empty">
          <AppIcon name="activity" :size="22" />
          <strong>暂时没有趋势数据</strong>
          <span>采集器开始工作后，这里会显示连接变化。</span>
        </div>
      </article>

      <article class="traffic-panel composition-panel">
        <div class="panel-header">
          <div>
            <h2>来源构成</h2>
            <p>按来源国家或地区统计</p>
          </div>
          <AppIcon name="globe" :size="20" class="panel-header-icon" />
        </div>
        <div v-if="geoCountries.length" class="composition-list">
          <div v-for="item in geoCountries" :key="item.code" class="composition-row">
            <div class="composition-label">
              <span>{{ countryLabel(item.code) }}</span>
              <strong>{{ formatNumber(item.connections) }}</strong>
            </div>
            <div class="composition-track">
              <span :style="{ width: `${item.percent}%` }" />
            </div>
            <small>{{ formatNumber(item.sources) }} 个来源 IP</small>
          </div>
        </div>
        <div v-else class="panel-empty compact">
          <span>暂无来源分布数据</span>
        </div>
      </article>
    </section>

    <section class="traffic-ranking-grid">
      <article class="traffic-panel ranking-panel">
        <div class="panel-header">
          <div>
            <h2>Top 来源 IP</h2>
            <p>最近 {{ rangeDays }} 天连接最多的来源</p>
          </div>
          <span class="panel-count">{{ formatNumber(sourceTotal) }}</span>
        </div>
        <div v-if="sources.length" class="ranking-list">
          <div v-for="(item, index) in sources" :key="item.ip" class="ranking-row">
            <span class="ranking-index">{{ String(index + 1).padStart(2, "0") }}</span>
            <span class="ranking-main">
              <strong class="mono">{{ maskIp(item.ip) }}</strong>
              <small>{{ item.place || item.org || "归属地未知" }}</small>
            </span>
            <span class="ranking-value">
              <strong>{{ formatNumber(item.connections) }}</strong>
              <small>{{ formatNumber(item.targets) }} 个目标</small>
            </span>
          </div>
        </div>
        <div v-else class="panel-empty compact"><span>暂无来源 IP</span></div>
      </article>

      <article class="traffic-panel ranking-panel">
        <div class="panel-header">
          <div>
            <h2>Top 目标域名</h2>
            <p>最近 {{ rangeDays }} 天访问最多的目标</p>
          </div>
          <AppIcon name="globe" :size="20" class="panel-header-icon" />
        </div>
        <div v-if="targets.length" class="ranking-list">
          <div v-for="(item, index) in targets" :key="item.domain" class="ranking-row">
            <span class="ranking-index">{{ String(index + 1).padStart(2, "0") }}</span>
            <span class="ranking-main">
              <strong class="mono domain-text">{{ item.domain || "未知目标" }}</strong>
              <small>最近 {{ item.last || "—" }}</small>
            </span>
            <span class="ranking-value">
              <strong>{{ formatNumber(item.connections) }}</strong>
              <small>{{ formatNumber(item.sources) }} 个来源</small>
            </span>
          </div>
        </div>
        <div v-else class="panel-empty compact"><span>暂无目标域名</span></div>
      </article>
    </section>

    <section class="traffic-panel live-panel">
      <div class="panel-header live-header">
        <div>
          <h2>实时连接</h2>
          <p>展示最近采集到的连接记录，不保存请求内容。</p>
        </div>
        <div class="live-controls">
          <span v-if="liveLoading" class="live-hint">正在更新…</span>
          <button
            type="button"
            class="auto-refresh-button"
            :class="{ 'is-active': liveAuto }"
            :aria-pressed="liveAuto"
            @click="liveAuto = !liveAuto"
          >
            <span class="auto-refresh-dot" aria-hidden="true" />
            自动刷新
          </button>
        </div>
      </div>
      <div v-if="live.length" class="live-table-wrap">
        <table class="live-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>来源 IP</th>
              <th>协议</th>
              <th>目标域名</th>
              <th class="numeric">端口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in live" :key="`${item.ts}-${item.sourceIp}-${item.target}-${item.port}`">
              <td class="muted mono">{{ item.t || "—" }}</td>
              <td class="mono">{{ maskIp(item.sourceIp) }}</td>
              <td><span class="network-label">{{ item.network || "—" }}</span></td>
              <td class="mono target-cell">{{ item.target || "—" }}</td>
              <td class="numeric muted">{{ item.port || "—" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="panel-empty compact"><span>暂无实时连接</span></div>
    </section>

    <section class="traffic-bottom-grid">
      <article class="traffic-panel alerts-panel">
        <div class="panel-header">
          <div>
            <h2>最近告警</h2>
            <p>来自 DMIT 采集器的告警记录</p>
          </div>
          <span class="alert-count" :class="{ 'is-warning': openAlerts > 0 }">{{ openAlerts }} 未读</span>
        </div>
        <div v-if="alerts.length" class="alert-list">
          <div v-for="item in alerts.slice(0, 4)" :key="item.id" class="alert-row">
            <span class="alert-marker" :class="alertTone(item.level)" />
            <div>
              <strong>{{ item.title || item.kind || "监控告警" }}</strong>
              <p>{{ item.detail || "暂无详情" }}</p>
            </div>
            <time>{{ item.t || "—" }}</time>
          </div>
        </div>
        <div v-else class="panel-empty compact"><span>暂无告警记录</span></div>
      </article>

      <article class="traffic-panel source-panel">
        <div class="panel-header">
          <div>
            <h2>数据源状态</h2>
            <p>只读连接与隐私保护</p>
          </div>
          <AppIcon name="activity" :size="20" class="panel-header-icon" />
        </div>
        <dl class="source-status-list">
          <div>
            <dt>Xray 服务</dt>
            <dd :class="`is-${xrayTone}`"><span class="status-dot" />{{ xrayLabel }}</dd>
          </div>
          <div>
            <dt>采集器</dt>
            <dd :class="`is-${collectorTone}`"><span class="status-dot" />{{ collectorLabel }}</dd>
          </div>
          <div>
            <dt>最近同步</dt>
            <dd>{{ updatedLabel }}</dd>
          </div>
          <div>
            <dt>敏感信息</dt>
            <dd>IP 默认脱敏</dd>
          </div>
        </dl>
      </article>
    </section>
  </div>
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";

export default {
  name: "TrafficView",
  components: { AppIcon },
  data() {
    return {
      rangeKey: "48h",
      rangeOptions: [
        { value: "24h", label: "最近 24 小时", hours: 24, days: 1, daily: false },
        { value: "48h", label: "最近 48 小时", hours: 48, days: 2, daily: false },
        { value: "7d", label: "最近 7 天", hours: 168, days: 7, daily: false },
        { value: "30d", label: "最近 30 天", hours: 720, days: 30, daily: true }
      ],
      trendMetric: "connections",
      trendMetricOptions: [
        { value: "connections", label: "连接数" },
        { value: "uniqueIps", label: "独立 IP" }
      ],
      loading: false,
      liveLoading: false,
      liveAuto: true,
      error: "",
      overview: null,
      trend: [],
      sources: [],
      sourceTotal: 0,
      targets: [],
      geo: { countries: [] },
      live: [],
      alerts: [],
      lastLoadedAt: null,
      liveTimer: null
    };
  },
  computed: {
    rangeMeta() {
      return this.rangeOptions.find(option => option.value === this.rangeKey) || this.rangeOptions[1];
    },
    rangeLabel() {
      return this.rangeMeta.label;
    },
    rangeDays() {
      return this.rangeMeta.days;
    },
    trendMetricLabel() {
      return this.trendMetric === "uniqueIps" ? "独立 IP" : "连接数";
    },
    metricCards() {
      const overview = this.overview || {};
      return [
        {
          label: "当前在线",
          value: this.formatNumber(overview.online),
          caption: "正在活跃的连接",
          icon: "activity",
          tone: "blue"
        },
        {
          label: "累计连接",
          value: this.formatNumber(overview.totalConnections),
          caption: `今日 ${this.formatNumber(overview.todayConnections)}`,
          icon: "file",
          tone: "green"
        },
        {
          label: "总流量",
          value: this.formatBytes((overview.trafficUp || 0) + (overview.trafficDown || 0)),
          caption: `↑ ${this.formatBytes(overview.trafficUp)} · ↓ ${this.formatBytes(overview.trafficDown)}`,
          icon: "refresh",
          tone: "purple"
        },
        {
          label: "目标域名",
          value: this.formatNumber(overview.totalDomains),
          caption: `${this.formatNumber(overview.totalIps)} 个来源 IP`,
          icon: "globe",
          tone: "orange"
        }
      ];
    },
    statusTone() {
      if (!this.overview) return "muted";
      if (this.xrayTone === "bad" || this.collectorTone === "bad") return "bad";
      if (this.collectorTone === "warn") return "warn";
      return "ok";
    },
    statusLabel() {
      if (!this.overview) return "等待数据";
      if (this.statusTone === "bad") return "数据源异常";
      if (this.statusTone === "warn") return "采集器延迟";
      return "监控正常";
    },
    xrayLabel() {
      return this.overview?.xray === "active" ? "运行中" : (this.overview?.xray || "未知");
    },
    xrayTone() {
      if (!this.overview) return "muted";
      return this.overview.xray === "active" ? "ok" : "bad";
    },
    collectorLabel() {
      const lag = this.overview?.collector?.lag;
      if (lag == null) return "未上报";
      if (lag < 90) return `${lag} 秒前`;
      return `${lag} 秒前`;
    },
    collectorTone() {
      const lag = this.overview?.collector?.lag;
      if (lag == null) return "bad";
      return lag < 90 ? "ok" : "warn";
    },
    updatedLabel() {
      if (!this.lastLoadedAt) return "尚未同步";
      return new Date(this.lastLoadedAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
    },
    openAlerts() {
      return Number(this.overview?.openAlerts || 0);
    },
    geoCountries() {
      const countries = Array.isArray(this.geo?.countries) ? this.geo.countries : [];
      const total = countries.reduce((sum, item) => sum + Number(item.connections || 0), 0) || 1;
      return countries.slice(0, 5).map(item => ({
        ...item,
        percent: Math.max(3, Math.round((Number(item.connections || 0) / total) * 100))
      }));
    },
    chartValues() {
      return this.trend.map(item => Number(item[this.trendMetric] || 0));
    },
    chartPoints() {
      const values = this.chartValues;
      const max = Math.max(1, ...values);
      const left = 44;
      const right = 744;
      const top = 18;
      const bottom = 214;
      const span = Math.max(1, values.length - 1);
      return values.map((value, index) => ({
        key: `${index}-${value}`,
        x: left + ((right - left) * index) / span,
        y: bottom - ((bottom - top) * value) / max,
        value
      }));
    },
    trendPolyline() {
      return this.chartPoints.map(point => `${point.x},${point.y}`).join(" ");
    },
    trendAreaPath() {
      if (!this.chartPoints.length) return "";
      const first = this.chartPoints[0];
      const last = this.chartPoints[this.chartPoints.length - 1];
      return `M ${first.x} 214 L ${this.chartPoints.map(point => `${point.x} ${point.y}`).join(" L ")} L ${last.x} 214 Z`;
    },
    chartGrid() {
      const max = Math.max(1, ...this.chartValues);
      return [0, 0.33, 0.66, 1].map((ratio, index) => ({
        label: this.formatNumber(Math.round(max * (1 - ratio))),
        y: 18 + 196 * ratio,
        key: index
      }));
    },
    chartLabels() {
      if (!this.trend.length) return [];
      const lastIndex = this.trend.length - 1;
      const indexes = [...new Set([0, Math.floor(lastIndex / 2), lastIndex])];
      return indexes.map((index, labelIndex) => ({
        key: index,
        x: this.chartPoints[index]?.x || 44,
        text: this.trend[index]?.label || "—",
        anchor: labelIndex === 0 ? "start" : labelIndex === indexes.length - 1 ? "end" : "middle"
      }));
    },
    trendPeak() {
      return Math.max(0, ...this.chartValues);
    }
  },
  watch: {
    rangeKey() {
      this.loadDashboard();
    },
    liveAuto(value) {
      if (value) this.startLiveTimer();
      else this.stopLiveTimer();
    }
  },
  created() {
    this.loadDashboard();
    this.startLiveTimer();
  },
  beforeUnmount() {
    this.stopLiveTimer();
  },
  methods: {
    async loadDashboard() {
      if (this.loading) return;
      this.loading = true;
      this.error = "";
      const request = this.rangeMeta.daily
        ? this.$api.admin.trafficDaily({ params: { days: this.rangeMeta.days } })
        : this.$api.admin.trafficTimeseries({ params: { hours: this.rangeMeta.hours } });
      try {
        const [overview, trend, sourcePage, targets, geo, live, alerts] = await Promise.all([
          this.$api.admin.trafficOverview(),
          request,
          this.$api.admin.trafficSources({ params: { days: this.rangeMeta.days, limit: 8 } }),
          this.$api.admin.trafficTargets({ params: { days: this.rangeMeta.days, limit: 8 } }),
          this.$api.admin.trafficGeo({ params: { days: this.rangeMeta.days } }),
          this.$api.admin.trafficLive({ params: { limit: 80 } }),
          this.$api.admin.trafficAlerts({ params: { limit: 20 } })
        ]);
        this.overview = overview.data || {};
        this.trend = Array.isArray(trend.data) ? trend.data : [];
        this.sources = Array.isArray(sourcePage.data?.items) ? sourcePage.data.items : [];
        this.sourceTotal = Number(sourcePage.data?.total || 0);
        this.targets = Array.isArray(targets.data) ? targets.data : [];
        this.geo = geo.data || { countries: [] };
        this.live = Array.isArray(live.data) ? live.data : [];
        this.alerts = Array.isArray(alerts.data) ? alerts.data : [];
        this.lastLoadedAt = Date.now();
      } catch {
        this.error = "代理监控数据暂时无法读取，请检查 DMIT 数据源连接";
      } finally {
        this.loading = false;
      }
    },
    async loadLive() {
      if (this.liveLoading) return;
      this.liveLoading = true;
      try {
        const response = await this.$api.admin.trafficLive({ params: { limit: 80 } });
        this.live = Array.isArray(response.data) ? response.data : [];
      } catch {
        // The dashboard keeps its last known rows when the five-second poll fails.
      } finally {
        this.liveLoading = false;
      }
    },
    startLiveTimer() {
      this.stopLiveTimer();
      if (this.liveAuto) this.liveTimer = window.setInterval(this.loadLive, 15000);
    },
    stopLiveTimer() {
      if (this.liveTimer) window.clearInterval(this.liveTimer);
      this.liveTimer = null;
    },
    maskIp(value) {
      if (!value) return "未知 IP";
      if (value.includes(":")) return `${value.split(":").slice(0, 3).join(":")}::*`;
      const parts = value.split(".");
      return parts.length === 4 ? `${parts[0]}.${parts[1]}.*.*` : value;
    },
    countryLabel(code) {
      if (!code || code === "??") return "未知地区";
      if (code === "CN") return "中国大陆";
      return code;
    },
    alertTone(level) {
      return `is-${level || "info"}`;
    },
    formatNumber(value) {
      return Number(value || 0).toLocaleString("zh-CN");
    },
    formatBytes(value) {
      let number = Number(value || 0);
      if (number < 1024) return `${number} B`;
      const units = ["KB", "MB", "GB", "TB"];
      let index = -1;
      do {
        number /= 1024;
        index += 1;
      } while (number >= 1024 && index < units.length - 1);
      return `${number.toFixed(number < 10 ? 2 : 1)} ${units[index]}`;
    }
  }
};
</script>

<style scoped>
.traffic-page {
  width: min(1240px, 100%);
  margin: 0 auto;
}

.traffic-header,
.panel-header,
.traffic-toolbar,
.live-controls,
.traffic-status,
.refresh-button,
.auto-refresh-button,
.trend-summary,
.composition-label,
.ranking-row,
.alert-row,
.source-status-list dd {
  display: flex;
  align-items: center;
}

.traffic-header {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.traffic-eyebrow {
  margin: 0 0 8px;
  color: var(--admin-blue);
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.traffic-eyebrow span {
  margin: 0 4px;
  color: var(--admin-text-tertiary);
}

.traffic-header h1 {
  margin: 0;
  color: var(--admin-text);
  font-size: clamp(30px, 4vw, 42px);
  font-weight: 750;
  letter-spacing: -0.04em;
}

.traffic-subtitle,
.panel-header p {
  margin: 9px 0 0;
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.traffic-toolbar {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 9px;
}

.traffic-status,
.range-control select,
.refresh-button,
.auto-refresh-button {
  min-height: 36px;
  border: 1px solid var(--admin-border);
  border-radius: 10px;
  background: var(--admin-surface);
}

.traffic-status {
  gap: 8px;
  padding: 0 12px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.traffic-status.is-ok {
  color: #218739;
  background: rgba(52, 199, 89, 0.07);
  border-color: rgba(52, 199, 89, 0.2);
}

.traffic-status.is-warn {
  color: #a66000;
  background: rgba(255, 159, 10, 0.08);
  border-color: rgba(255, 159, 10, 0.22);
}

.traffic-status.is-bad {
  color: #c72d25;
  background: rgba(255, 59, 48, 0.07);
  border-color: rgba(255, 59, 48, 0.2);
}

.status-dot,
.auto-refresh-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--admin-text-tertiary);
}

.traffic-status.is-ok .status-dot,
.source-status-list dd.is-ok .status-dot {
  background: var(--admin-green);
}

.traffic-status.is-warn .status-dot,
.source-status-list dd.is-warn .status-dot {
  background: var(--admin-orange);
}

.traffic-status.is-bad .status-dot,
.source-status-list dd.is-bad .status-dot {
  background: var(--admin-red);
}

.range-control select {
  padding: 0 30px 0 11px;
  color: var(--admin-text-secondary);
  font-size: 12px;
  cursor: pointer;
}

.refresh-button {
  justify-content: center;
  gap: 7px;
  padding: 0 13px;
  color: var(--admin-blue);
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.refresh-button:hover,
.auto-refresh-button:hover {
  border-color: rgba(0, 113, 227, 0.35);
  background: var(--admin-blue-soft);
}

.refresh-button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.is-spinning {
  animation: traffic-spin 900ms linear infinite;
}

@keyframes traffic-spin {
  to { transform: rotate(360deg); }
}

.traffic-error {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 14px;
  margin: 0 0 16px;
  color: #b42318;
  font-size: 13px;
  background: #fff3f1;
  border: 1px solid #ffd3ce;
  border-radius: 12px;
}

.traffic-error button {
  margin-left: auto;
  color: inherit;
  font-size: 12px;
  font-weight: 650;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.traffic-metrics,
.traffic-main-grid,
.traffic-ranking-grid,
.traffic-bottom-grid {
  display: grid;
  gap: 14px;
}

.traffic-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
}

.traffic-metric-card,
.traffic-panel {
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius);
  box-shadow: var(--admin-shadow);
}

.traffic-metric-card {
  display: flex;
  min-width: 0;
  gap: 13px;
  padding: 20px;
}

.metric-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
}

.metric-icon.is-blue { color: var(--admin-blue); background: var(--admin-blue-soft); }
.metric-icon.is-green { color: #218739; background: rgba(52, 199, 89, 0.1); }
.metric-icon.is-purple { color: #8a3ab9; background: rgba(175, 82, 222, 0.1); }
.metric-icon.is-orange { color: #b56b00; background: rgba(255, 159, 10, 0.12); }

.metric-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.metric-label {
  color: var(--admin-text-secondary);
  font-size: 12px;
  font-weight: 650;
}

.metric-copy strong {
  margin-top: 3px;
  color: var(--admin-text);
  font-size: 25px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.025em;
}

.metric-caption {
  overflow: hidden;
  margin-top: 4px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.traffic-main-grid {
  grid-template-columns: minmax(0, 1.72fr) minmax(300px, 0.9fr);
  margin-bottom: 14px;
}

.traffic-ranking-grid,
.traffic-bottom-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 14px;
}

.traffic-panel {
  min-width: 0;
  padding: 22px;
}

.panel-header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-header h2 {
  margin: 0;
  color: var(--admin-text);
  font-size: 17px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.panel-header-icon {
  flex: 0 0 auto;
  color: var(--admin-blue);
}

.metric-switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--admin-surface-soft);
  border: 1px solid var(--admin-border);
  border-radius: 9px;
}

.metric-switch button {
  padding: 5px 8px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.metric-switch button:hover,
.metric-switch button.is-active {
  color: var(--admin-blue);
  background: #fff;
  box-shadow: 0 2px 8px rgba(29, 29, 31, 0.06);
}

.trend-chart-wrap {
  min-width: 0;
}

.trend-chart {
  display: block;
  width: 100%;
  height: 248px;
  overflow: visible;
}

.chart-grid-line {
  stroke: var(--admin-border);
  stroke-dasharray: 3 5;
  stroke-width: 1;
}

.chart-axis-label {
  fill: var(--admin-text-tertiary);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.trend-area {
  fill: url(#traffic-trend-fill);
}

.trend-line {
  fill: none;
  stroke: var(--admin-blue);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.5;
}

.trend-point {
  fill: var(--admin-surface);
  stroke: var(--admin-blue);
  stroke-width: 2;
}

.trend-summary {
  justify-content: space-between;
  padding: 0 4px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.panel-empty {
  display: flex;
  min-height: 248px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: var(--admin-text-tertiary);
  text-align: center;
}

.panel-empty strong {
  color: var(--admin-text-secondary);
  font-size: 13px;
}

.panel-empty.compact {
  min-height: 130px;
  font-size: 13px;
}

.composition-list {
  display: flex;
  flex-direction: column;
  gap: 17px;
  padding-top: 4px;
}

.composition-row {
  display: grid;
  gap: 7px;
}

.composition-label {
  justify-content: space-between;
  gap: 12px;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.composition-label strong {
  color: var(--admin-text);
  font-variant-numeric: tabular-nums;
}

.composition-track {
  height: 7px;
  overflow: hidden;
  background: #eef0f4;
  border-radius: 999px;
}

.composition-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--admin-blue), #58a8f2);
  border-radius: inherit;
}

.composition-row small,
.ranking-row small {
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.panel-count,
.alert-count {
  color: var(--admin-text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.ranking-list {
  display: flex;
  flex-direction: column;
}

.ranking-row {
  min-width: 0;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--admin-border);
}

.ranking-row:first-child {
  padding-top: 0;
  border-top: 0;
}

.ranking-index {
  width: 21px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.ranking-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.ranking-main strong {
  overflow: hidden;
  color: var(--admin-text);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-value {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 3px;
}

.ranking-value strong {
  color: var(--admin-text);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.live-panel {
  margin-bottom: 14px;
}

.live-header {
  margin-bottom: 14px;
}

.live-controls {
  gap: 11px;
}

.live-hint {
  color: var(--admin-text-tertiary);
  font-size: 11px;
}

.auto-refresh-button {
  gap: 7px;
  padding: 0 10px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  cursor: pointer;
}

.auto-refresh-button.is-active {
  color: var(--admin-blue);
  border-color: rgba(0, 113, 227, 0.25);
  background: var(--admin-blue-soft);
}

.auto-refresh-button.is-active .auto-refresh-dot {
  background: var(--admin-blue);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.12);
}

.live-table-wrap {
  overflow-x: auto;
}

.live-table {
  width: 100%;
  min-width: 680px;
  border-collapse: collapse;
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.live-table th {
  padding: 0 12px 10px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.live-table td {
  padding: 11px 12px;
  border-top: 1px solid var(--admin-border);
  white-space: nowrap;
}

.live-table td:first-child,
.live-table th:first-child {
  padding-left: 0;
}

.live-table td:last-child,
.live-table th:last-child {
  padding-right: 0;
}

.numeric {
  text-align: right !important;
}

.muted {
  color: var(--admin-text-tertiary);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.target-cell {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.network-label {
  display: inline-flex;
  padding: 3px 7px;
  color: var(--admin-blue);
  font-size: 10px;
  background: var(--admin-blue-soft);
  border-radius: 6px;
}

.alert-count.is-warning {
  color: #b56b00;
}

.alert-list {
  display: flex;
  flex-direction: column;
}

.alert-row {
  align-items: flex-start;
  gap: 11px;
  padding: 11px 0;
  border-top: 1px solid var(--admin-border);
}

.alert-row:first-child {
  padding-top: 0;
  border-top: 0;
}

.alert-marker {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--admin-text-tertiary);
}

.alert-marker.is-high,
.alert-marker.is-critical {
  background: var(--admin-red);
}

.alert-marker.is-medium,
.alert-marker.is-warn {
  background: var(--admin-orange);
}

.alert-row > div {
  min-width: 0;
  flex: 1;
}

.alert-row strong {
  color: var(--admin-text);
  font-size: 12px;
}

.alert-row p {
  overflow: hidden;
  margin: 3px 0 0;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-row time {
  color: var(--admin-text-tertiary);
  font-size: 10px;
  white-space: nowrap;
}

.source-status-list {
  display: grid;
  gap: 0;
  margin: 0;
}

.source-status-list > div {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--admin-border);
}

.source-status-list > div:first-child {
  padding-top: 0;
  border-top: 0;
}

.source-status-list dt,
.source-status-list dd {
  margin: 0;
  font-size: 12px;
}

.source-status-list dt {
  color: var(--admin-text-tertiary);
}

.source-status-list dd {
  gap: 7px;
  color: var(--admin-text-secondary);
  text-align: right;
}

.source-status-list dd.is-ok { color: #218739; }
.source-status-list dd.is-warn { color: #a66000; }
.source-status-list dd.is-bad { color: #c72d25; }

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

@media (max-width: 1080px) {
  .traffic-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .traffic-main-grid {
    grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.9fr);
  }
}

@media (max-width: 820px) {
  .traffic-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .traffic-toolbar {
    justify-content: flex-start;
  }

  .traffic-main-grid,
  .traffic-ranking-grid,
  .traffic-bottom-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 520px) {
  .traffic-header {
    margin-bottom: 22px;
  }

  .traffic-toolbar {
    width: 100%;
  }

  .traffic-status {
    flex: 1;
  }

  .range-control {
    flex: 1;
  }

  .range-control select,
  .refresh-button {
    width: 100%;
  }

  .traffic-metrics {
    gap: 9px;
  }

  .traffic-metric-card {
    align-items: flex-start;
    gap: 9px;
    padding: 14px;
  }

  .metric-icon {
    width: 32px;
    height: 32px;
  }

  .metric-copy strong {
    font-size: 20px;
  }

  .metric-caption {
    max-width: 100px;
  }

  .traffic-panel {
    padding: 17px;
  }

  .panel-header {
    align-items: flex-start;
  }

  .trend-summary {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .live-header {
    flex-direction: column;
  }

  .live-controls {
    width: 100%;
    justify-content: space-between;
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-spinning {
    animation: none;
  }
}
</style>
