<template>
  <section class="outbox-panel" aria-labelledby="outbox-title">
    <div class="outbox-header">
      <div>
        <h3 id="outbox-title">可靠事件</h3>
        <p>事件先写入 MySQL；Redis Streams 只负责可选传输，重试不会绕过幂等 handler。</p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="outbox-metrics" aria-live="polite">
      <span v-for="item in metricEntries" :key="item[0]" class="metric-chip">
        {{ item[0] }}：{{ item[1] }}
      </span>
    </div>
    <el-alert v-if="error" type="error" :closable="false" show-icon>
      {{ error }}
    </el-alert>
    <el-table v-loading="loading" :data="events" size="small" row-key="eventId">
      <el-table-column prop="eventType" label="事件类型" min-width="180" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="attempts" label="尝试" width="70" />
      <el-table-column prop="aggregateId" label="聚合对象" min-width="130" />
      <el-table-column prop="createdAt" label="创建时间" min-width="170" />
      <el-table-column label="最近错误" min-width="220">
        <template #default="{ row }">{{ row.lastError || "—" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canRetry(row)"
            link
            type="primary"
            :loading="retrying === row.eventId"
            @click="retry(row)"
          >重试</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > pageSize"
      class="outbox-pagination"
      background
      layout="prev, pager, next"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      @current-change="changePage"
    />
  </section>
</template>

<script>
export default {
  name: "OutboxPanel",
  data() {
    return {
      events: [],
      metrics: {},
      currentPage: 1,
      pageSize: 20,
      total: 0,
      loading: false,
      retrying: "",
      error: ""
    };
  },
  computed: {
    metricEntries() {
      return Object.entries(this.metrics);
    }
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = "";
      try {
        const [listResponse, metricsResponse] = await Promise.all([
          this.$api.admin.outbox({ params: { current: this.currentPage, size: this.pageSize } }),
          this.$api.admin.outboxMetrics()
        ]);
        this.events = listResponse.data?.recordList || [];
        this.total = listResponse.data?.count || 0;
        this.metrics = metricsResponse.data || {};
      } catch {
        this.error = "可靠事件暂时无法读取，请稍后重试";
      } finally {
        this.loading = false;
      }
    },
    changePage(page) {
      this.currentPage = page;
      return this.load();
    },
    canRetry(row) {
      return ["DEAD", "ENQUEUED", "PROCESSING"].includes(row.status);
    },
    statusType(status) {
      return status === "PUBLISHED" ? "success" : status === "DEAD" ? "danger" : "warning";
    },
    async retry(row) {
      if (!window.confirm("确认重试这个可靠事件吗？")) return;
      this.retrying = row.eventId;
      try {
        await this.$api.admin.retryOutbox(row.eventId);
        this.$message.success("事件已重新排队");
        await this.load();
      } catch {
        this.error = "事件重试失败，请稍后重试";
      } finally {
        this.retrying = "";
      }
    }
  }
};
</script>

<style scoped>
.outbox-panel { max-width: 1180px; margin: 1.5rem auto; }
.outbox-header { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }
.outbox-header h3 { margin: 0 0 0.5rem; }
.outbox-header p { color: #7c8494; font-size: 0.875rem; }
.outbox-metrics { display: flex; flex-wrap: wrap; gap: 0.5rem; margin: 1rem 0; }
.metric-chip { padding: 0.35rem 0.65rem; border-radius: 999px; background: #f2f5fa; color: #536174; font-size: 0.8125rem; }
.outbox-pagination { justify-content: flex-end; margin-top: 1rem; }
</style>
