<template>
  <div v-show="visible" class="site-tools">
    <button type="button" class="tool-button" :aria-label="icon === 'iconyueliang' ? '切换深色模式' : '切换浅色模式'" @click="check">
      <i :class="['iconfont', icon]" aria-hidden="true" />
    </button>
    <button type="button" class="tool-button tool-top" aria-label="回到顶部" @click="backTop">
      <span aria-hidden="true">↑</span>
    </button>
  </div>
</template>

<script>
export default {
  data() {
    return {
      visible: false,
      icon: "iconyueliang"
    };
  },
  mounted() {
    window.addEventListener("scroll", this.scrollToTop, { passive: true });
    this.scrollToTop();
  },
  unmounted() {
    window.removeEventListener("scroll", this.scrollToTop);
  },
  methods: {
    backTop() {
      window.scrollTo({ behavior: "smooth", top: 0 });
    },
    scrollToTop() {
      this.visible = window.pageYOffset > 100;
    },
    check() {
      const isLight = this.icon === "iconyueliang";
      this.icon = isLight ? "icontaiyang" : "iconyueliang";
      this.$vuetify.theme.global.name.value = isLight ? "dark" : "light";
    }
  }
};
</script>

<style scoped>
.site-tools {
  position: fixed;
  right: 22px;
  bottom: 28px;
  z-index: 10;
  display: grid;
  gap: 8px;
}

.tool-button {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  color: var(--sage-deep);
  background: rgba(248, 247, 243, 0.9);
  box-shadow: 0 8px 24px rgba(56, 64, 73, 0.1);
  font-size: 15px;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
}

.tool-button:hover {
  color: #fff;
  background: var(--sage-deep);
  transform: translateY(-2px);
}

.tool-top {
  font-size: 19px;
}

@media (max-width: 760px) {
  .site-tools {
    right: 14px;
    bottom: 18px;
  }

  .tool-button {
    width: 38px;
    height: 38px;
  }
}
</style>
