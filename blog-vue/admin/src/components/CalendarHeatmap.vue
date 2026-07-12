<template>
  <div class="calendar-heatmap" aria-label="文章贡献统计">
    <span
      v-for="day in days"
      :key="day.date"
      class="calendar-heatmap__day"
      :class="`calendar-heatmap__day--${day.level}`"
      :title="`${day.date}: ${day.count}`"
    />
  </div>
</template>

<script>
import dayjs from "dayjs";

export default {
  props: {
    endDate: {
      type: [Date, String, Number],
      required: true
    },
    values: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    days() {
      const countByDate = new Map(this.values.map(item => [dayjs(item.date).format("YYYY-MM-DD"), item.count]));
      const max = Math.max(0, ...this.values.map(item => item.count));
      const end = dayjs(this.endDate).startOf("day");
      return Array.from({ length: 371 }, (_, index) => {
        const date = end.subtract(370 - index, "day").format("YYYY-MM-DD");
        const count = countByDate.get(date) || 0;
        const level = count === 0 ? 0 : Math.min(4, Math.ceil((count / max) * 4));
        return { date, count, level };
      });
    }
  }
};
</script>

<style scoped>
.calendar-heatmap {
  display: grid;
  gap: 0.25rem;
  grid-auto-flow: column;
  grid-template-rows: repeat(7, 0.75rem);
  overflow-x: auto;
  padding: 0.5rem 0;
}

.calendar-heatmap__day {
  background: #ebedf0;
  border-radius: 2px;
  height: 0.75rem;
  width: 0.75rem;
}

.calendar-heatmap__day--1 { background: #c6e48b; }
.calendar-heatmap__day--2 { background: #7bc96f; }
.calendar-heatmap__day--3 { background: #239a3b; }
.calendar-heatmap__day--4 { background: #196127; }
</style>
