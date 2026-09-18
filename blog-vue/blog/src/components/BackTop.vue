<template>
  <div v-show="visible" class="site-tools">
    <button
      type="button"
      class="tool-button"
      :aria-label="isDark ? '切换浅色模式' : '切换深色模式'"
      :aria-pressed="isDark"
      @click="check"
    >
      <svg v-if="isDark" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
      <svg v-else viewBox="0 0 24 24" aria-hidden="true">
        <path d="M20.7 15.6A8.5 8.5 0 0 1 8.4 3.3 8.5 8.5 0 1 0 20.7 15.6Z" />
      </svg>
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
      themeName: "light"
    };
  },
  computed: {
    isDark() {
      return this.themeName === "dark";
    }
  },
  mounted() {
    this.restoreTheme();
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
    getVuetifyThemeName() {
      const name = this.$vuetify?.theme?.global?.name;
      return typeof name === "string" ? name : name?.value || "light";
    },
    restoreTheme() {
      let savedTheme = "";
      try {
        savedTheme = window.localStorage.getItem("ticastr-theme") || "";
      } catch {
        // Local storage can be unavailable in private browsing contexts.
      }
      this.applyTheme(savedTheme === "dark" ? "dark" : this.getVuetifyThemeName(), false);
    },
    applyTheme(themeName, persist = true) {
      const nextTheme = themeName === "dark" ? "dark" : "light";
      const vuetifyTheme = this.$vuetify?.theme;
      const globalTheme = this.$vuetify?.theme?.global;
      const globalName = globalTheme?.name;
      if (typeof vuetifyTheme?.change === "function") {
        vuetifyTheme.change(nextTheme);
      } else if (globalName && typeof globalName === "object" && "value" in globalName) {
        globalName.value = nextTheme;
      } else if (globalTheme) {
        globalTheme.name = nextTheme;
      }
      this.themeName = nextTheme;
      document.documentElement.dataset.theme = nextTheme;
      document.documentElement.style.colorScheme = nextTheme;
      if (persist) {
        try {
          window.localStorage.setItem("ticastr-theme", nextTheme);
        } catch {
          // Keep the current theme even when persistence is unavailable.
        }
      }
    },
    check() {
      this.applyTheme(this.isDark ? "light" : "dark");
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

.tool-button svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
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
