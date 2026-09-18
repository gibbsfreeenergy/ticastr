<template>
  <v-app-bar :class="['site-nav', 'orbit-nav', navClass]" flat height="78">
    <div class="nav-shell">
      <router-link class="site-brand" to="/" aria-label="返回首页">
        <span class="brand-mark">t</span>
        <span class="brand-copy">
          <strong>ticastr</strong>
          <small>记录生活，分享技术</small>
        </span>
      </router-link>

      <nav class="desktop-nav" aria-label="主导航">
        <router-link class="nav-link menu-btn" to="/" :class="{ active: $route.path === '/' }">首页</router-link>
        <router-link class="nav-link menu-btn" to="/archives" :class="{ active: $route.path === '/archives' }">归档</router-link>
        <router-link class="nav-link menu-btn" to="/about" :class="{ active: $route.path === '/about' }">关于</router-link>
        <span class="nav-divider" aria-hidden="true" />
        <button type="button" class="nav-search menu-btn" @click="openSearch">
          <NavIcon name="search" />
          <span>搜索</span>
        </button>
      </nav>

      <div :class="['mobile-nav-actions', { 'drawer-open': $store.state.drawer }]">
        <button
          type="button"
          class="nav-icon-button"
          aria-label="搜索"
          :aria-expanded="$store.state.searchFlag"
          @click="openSearch"
        >
          <NavIcon name="search" />
        </button>
        <button
          type="button"
          class="nav-icon-button"
          aria-label="打开菜单"
          :aria-expanded="$store.state.drawer"
          @click="openDrawer"
        >
          <NavIcon name="menu" />
        </button>
      </div>
    </div>
  </v-app-bar>
</template>

<script>
import NavIcon from "../NavIcon.vue";

export default {
  components: { NavIcon },
  mounted() {
    this.updateNavigation();
    window.addEventListener("scroll", this.updateNavigation, { passive: true });
  },
  beforeUnmount() {
    window.removeEventListener("scroll", this.updateNavigation);
  },
  data() {
    return {
      navClass: "nav-overlay"
    };
  },
  watch: {
    "$route.path"() {
      this.updateNavigation();
    }
  },
  methods: {
    updateNavigation() {
      const scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
      this.navClass = scrollTop > 48 || this.$route.path !== "/" ? "nav-fixed" : "nav-overlay";
    },
    openSearch() {
      this.$store.state.searchFlag = true;
    },
    openDrawer() {
      this.$store.state.drawer = true;
    }
  }
};
</script>

<style scoped>
.site-nav :deep(.v-toolbar__content) {
  overflow: visible !important;
}

.site-nav {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 20;
  height: 78px;
  color: var(--paper-strong);
  transition: background 240ms ease, color 240ms ease, box-shadow 240ms ease;
}

.nav-overlay {
  background: linear-gradient(180deg, rgba(18, 32, 43, 0.3), transparent);
}

.nav-fixed {
  color: var(--ink);
  background: var(--glass-surface-strong);
  box-shadow: 0 1px 0 var(--line);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
}

.nav-shell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(var(--content-width), calc(100% - 64px));
  height: 100%;
  margin: 0 auto;
}

.site-brand {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  min-width: 150px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 21px;
  line-height: 1;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.brand-copy strong {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.brand-copy small {
  margin-top: 5px;
  font-size: 9px;
  letter-spacing: 0.14em;
  opacity: 0.72;
}

.desktop-nav,
.mobile-nav-actions {
  display: flex;
  align-items: center;
}

.desktop-nav {
  gap: 28px;
  font-size: 14px;
}

.nav-link,
.nav-search {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  color: inherit;
  background: transparent;
  font-size: inherit;
  transition: color 180ms ease;
}

.nav-link::after {
  position: absolute;
  right: 0;
  bottom: -9px;
  left: 0;
  height: 2px;
  background: var(--sage);
  content: "";
  opacity: 0;
  transform: scaleX(0.4);
  transition: opacity 180ms ease, transform 180ms ease;
}

.nav-link:hover,
.nav-link.active,
.nav-search:hover {
  color: var(--sage-deep);
}

.nav-link.active::after {
  opacity: 1;
  transform: scaleX(1);
}

.nav-divider {
  width: 1px;
  height: 20px;
  background: currentColor;
  opacity: 0.24;
}

.nav-search {
  padding: 0;
}

.nav-search :deep(.nav-icon-svg) {
  width: 16px;
  height: 16px;
}

.nav-search i {
  font-size: 16px;
}

.mobile-nav-actions {
  display: none;
  gap: 2px;
  padding: 4px;
  border: 1px solid var(--glass-border);
  border-radius: 999px;
  background: var(--glass-surface);
  box-shadow: inset 0 1px 0 var(--glass-highlight), 0 8px 26px rgba(24, 32, 43, 0.14);
  -webkit-backdrop-filter: blur(22px) saturate(175%);
  backdrop-filter: blur(22px) saturate(175%);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.nav-icon-button {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  color: inherit;
  background: transparent;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.nav-icon-button:hover {
  background: var(--glass-highlight);
  color: var(--sage-deep);
  transform: translateY(-1px);
}

.nav-icon-button:active {
  transform: scale(0.94);
}

.mobile-nav-actions.drawer-open {
  border-color: color-mix(in srgb, var(--sage) 70%, var(--glass-border));
  box-shadow: inset 0 1px 0 var(--glass-highlight), 0 10px 30px rgba(85, 118, 107, 0.2);
}

.mobile-nav-actions.drawer-open .nav-icon-button:last-child {
  color: var(--sage-deep);
  background: color-mix(in srgb, var(--sage) 18%, transparent);
}

@media (max-width: 760px) {
  .site-nav {
    height: 68px;
  }

  .nav-shell {
    width: min(100% - 28px, var(--content-width));
  }

  .site-brand {
    min-width: auto;
  }

  .brand-copy small {
    display: none;
  }

  .brand-copy strong {
    font-size: 18px;
  }

  .desktop-nav {
    display: none;
  }

  .mobile-nav-actions {
    display: flex;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .mobile-nav-actions {
    background: var(--paper-strong);
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}
</style>
