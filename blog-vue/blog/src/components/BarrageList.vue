<template>
  <div class="barrage-list" aria-live="polite">
    <div
      v-for="(message, index) in messages"
      :key="`${message.id || message.time}-${index}`"
      class="barrage-track"
      :style="trackStyle(message, index)"
    >
      <span class="barrage-item">
        <img :src="message.avatar" width="30" height="30" alt="" />
        <span class="ml-2">{{ message.nickname }} :</span>
        <span class="ml-2">{{ message.messageContent }}</span>
      </span>
    </div>
  </div>
</template>

<script>
export default {
  name: "BarrageList",
  props: {
    messages: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    trackStyle(message, index) {
      return {
        "--barrage-delay": `${(index % 8) * 0.8}s`,
        "--barrage-duration": `${message.time || 8}s`,
        top: `${8 + (index * 13) % 78}%`
      };
    }
  }
};
</script>

<style scoped>
.barrage-list {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
.barrage-track {
  position: absolute;
  left: 100%;
  white-space: nowrap;
  animation: barrage var(--barrage-duration) linear var(--barrage-delay) infinite;
}
.barrage-item {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px 5px 5px;
  color: #fff;
  background: rgb(0 0 0 / 70%);
  border-radius: 100px;
}
.barrage-item img {
  border-radius: 50%;
}
@keyframes barrage {
  to {
    transform: translateX(calc(-100vw - 100%));
  }
}
@media (prefers-reduced-motion: reduce) {
  .barrage-track {
    animation: none;
  }
}
</style>
