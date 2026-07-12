<template>
  <div class="tag-cloud" aria-label="文章标签统计">
    <span
      v-for="(tag, index) in data"
      :key="tag.id || tag.tagName"
      class="tag-cloud__tag"
      :style="tagStyle(index)"
    >
      {{ tag.tagName }}
    </span>
  </div>
</template>

<script>
export default {
  props: {
    data: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    tagStyle(index) {
      return {
        color: `hsl(${(index * 47) % 360} 65% 42%)`,
        fontSize: `${1 + (index % 4) * 0.15}rem`
      };
    }
  }
};
</script>

<style scoped>
.tag-cloud {
  align-content: center;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: center;
  min-height: 12rem;
  padding: 1rem;
}

.tag-cloud__tag {
  background: #f4f4f5;
  border-radius: 999px;
  padding: 0.35rem 0.75rem;
}
</style>
