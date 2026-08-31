<template>
  <aside
    class="sidebar-shell"
    :class="{ 'is-collapsed': isCollapsed, 'is-mobile-open': mobileOpen }"
    aria-label="主导航"
  >
    <div class="sidebar-brand">
      <button class="brand-lockup" type="button" @click="goHome" aria-label="返回首页">
        <span class="brand-mark">t</span>
        <span class="brand-name">ticastr</span>
      </button>
      <button
        class="sidebar-toggle"
        type="button"
        :aria-label="isCollapsed ? '展开导航' : '收起导航'"
        :title="isCollapsed ? '展开导航' : '收起导航'"
        @click="toggleCollapse"
      >
        <AppIcon :name="isCollapsed ? 'chevronRight' : 'chevronLeft'" :size="18" />
      </button>
    </div>

    <div class="sidebar-scroll">
      <nav class="sidebar-nav">
        <section v-for="section in menuSections" :key="section.label" class="nav-section">
          <p class="nav-section-label">{{ section.label }}</p>
          <button
            v-for="item in section.items"
            :key="item.path"
            type="button"
            class="nav-item"
            :class="{ 'is-active': isActive(item) }"
            :title="isCollapsed ? item.name : undefined"
            @click="selectItem(item)"
          >
            <span class="nav-item-icon"><AppIcon :name="iconFor(item)" :size="18" /></span>
            <span class="nav-item-label">{{ item.name }}</span>
            <span class="nav-item-indicator" aria-hidden="true" />
          </button>
        </section>
      </nav>
    </div>

    <div class="sidebar-footer">
      <button
        class="sidebar-footer-button"
        type="button"
        :aria-label="isCollapsed ? '展开导航' : '收起导航'"
        :title="isCollapsed ? '展开导航' : '收起导航'"
        @click="toggleCollapse"
      >
        <AppIcon :name="isCollapsed ? 'chevronRight' : 'chevronLeft'" :size="16" />
        <span>{{ isCollapsed ? "展开导航" : "收起导航" }}</span>
      </button>
    </div>
  </aside>
  <button
    v-if="mobileOpen"
    class="mobile-scrim"
    type="button"
    aria-label="关闭导航"
    @click="$emit('close')"
  />
</template>

<script>
import AppIcon from "../../components/AppIcon.vue";
import { sectionLabel } from "../../assets/js/menuMetadata";

export default {
  name: "SideBar",
  components: { AppIcon },
  emits: ["close"],
  props: {
    mobileOpen: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    isCollapsed() {
      return this.$store.state.collapse;
    },
    menuSections() {
      const sections = [];
      const entries = [];
      this.$store.state.userMenuList.forEach(route => {
        const children = Array.isArray(route.children) ? route.children : [];
        if (children.length > 0) {
          children.filter(item => !item.hidden).forEach(item => {
            entries.push({
              ...item,
              path: this.normalizePath(item.path || route.path)
            });
          });
        } else if (!route.hidden && route.name) {
          entries.push({ ...route, path: this.normalizePath(route.path) });
        }
      });
      entries.forEach(item => {
        const label = this.sectionFor(item.section);
        let section = sections.find(current => current.label === label);
        if (!section) {
          section = { label, items: [] };
          sections.push(section);
        }
        section.items.push(item);
      });
      return sections;
    }
  },
  methods: {
    normalizePath(path) {
      if (!path || path === "home") return "/";
      return path.startsWith("/") ? path : `/${path}`;
    },
    sectionFor(section) {
      return sectionLabel(section);
    },
    iconFor(item) {
      return item.iconKey || "grid";
    },
    isActive(item) {
      const currentPath = this.$route.path;
      return currentPath === item.path
        || (item.path !== "/" && currentPath.startsWith(`${item.path}/`));
    },
    selectItem(item) {
      this.$router.push({ path: item.path });
      this.$emit("close");
    },
    goHome() {
      this.$router.push({ path: "/" });
      this.$emit("close");
    },
    toggleCollapse() {
      if (this.mobileOpen) {
        this.$emit("close");
        return;
      }
      this.$store.commit("trigger");
    }
  }
};
</script>

<style scoped>
.sidebar-shell {
  position: fixed;
  z-index: 40;
  inset: 0 auto 0 0;
  display: flex;
  width: 248px;
  flex-direction: column;
  color: var(--admin-text-secondary);
  background: rgba(255, 255, 255, 0.88);
  border-right: 1px solid var(--admin-border);
  backdrop-filter: blur(24px) saturate(150%);
  transition: width 360ms cubic-bezier(0.22, 1, 0.36, 1), transform 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 76px;
  padding: 0 16px 0 22px;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 0;
  color: var(--admin-blue);
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.brand-mark {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.06em;
  background: var(--admin-blue);
  border-radius: 9px;
}

.brand-name {
  overflow: hidden;
  color: var(--admin-text);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.04em;
  white-space: nowrap;
}

.sidebar-toggle,
.sidebar-footer-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.sidebar-toggle {
  width: 32px;
  height: 32px;
  border-color: var(--admin-border);
  border-radius: 50%;
}

.sidebar-toggle:hover,
.sidebar-footer-button:hover {
  color: var(--admin-blue);
  background: var(--admin-blue-soft);
}

.sidebar-toggle:active,
.sidebar-footer-button:active {
  transform: scale(0.94);
}

.sidebar-scroll {
  flex: 1;
  padding: 12px 12px 16px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(110, 110, 115, 0.24) transparent;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.nav-section-label {
  margin: 0 12px 8px;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.06em;
  line-height: 1.3;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 42px;
  gap: 12px;
  padding: 0 12px;
  color: var(--admin-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 520;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 12px;
  cursor: pointer;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.nav-item:hover {
  color: var(--admin-text);
  background: rgba(118, 118, 128, 0.08);
}

.nav-item:active {
  transform: scale(0.985);
}

.nav-item.is-active {
  color: var(--admin-blue);
  font-weight: 650;
  background: var(--admin-blue-soft);
}

.nav-item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  color: currentColor;
}

.nav-item-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-item-indicator {
  width: 5px;
  height: 5px;
  margin-left: auto;
  background: currentColor;
  border-radius: 50%;
  opacity: 0;
  transform: scale(0.5);
  transition: opacity 180ms ease, transform 180ms ease;
}

.nav-item.is-active .nav-item-indicator {
  opacity: 1;
  transform: scale(1);
}

.sidebar-footer {
  padding: 12px 16px 18px;
  border-top: 1px solid rgba(229, 229, 234, 0.7);
}

.sidebar-footer-button {
  justify-content: flex-start;
  width: 100%;
  min-height: 34px;
  gap: 10px;
  padding: 0 10px;
  font-size: 12px;
  border-radius: 10px;
}

.sidebar-shell.is-collapsed {
  width: 76px;
}

.sidebar-shell.is-collapsed .sidebar-brand {
  padding: 0;
  justify-content: center;
}

.sidebar-shell.is-collapsed .brand-name,
.sidebar-shell.is-collapsed .sidebar-toggle,
.sidebar-shell.is-collapsed .nav-section-label,
.sidebar-shell.is-collapsed .nav-item-label,
.sidebar-shell.is-collapsed .nav-item-indicator,
.sidebar-shell.is-collapsed .sidebar-footer-button span {
  display: none;
}

.sidebar-shell.is-collapsed .sidebar-scroll {
  padding-right: 12px;
  padding-left: 12px;
}

.sidebar-shell.is-collapsed .nav-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar-shell.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

.sidebar-shell.is-collapsed .nav-item-icon {
  width: auto;
}

.sidebar-shell.is-collapsed .sidebar-footer-button {
  justify-content: center;
  padding: 0;
}

.mobile-scrim {
  position: fixed;
  z-index: 30;
  inset: 0;
  width: 100%;
  height: 100%;
  background: rgba(29, 29, 31, 0.2);
  border: 0;
  backdrop-filter: blur(3px);
}

@media (max-width: 900px) {
  .sidebar-shell,
  .sidebar-shell.is-collapsed {
    width: min(84vw, 300px);
    transform: translateX(-105%);
    box-shadow: none;
  }

  .sidebar-shell.is-mobile-open {
    transform: translateX(0);
    box-shadow: 18px 0 50px rgba(29, 29, 31, 0.14);
  }

  .sidebar-shell.is-collapsed .sidebar-brand {
    padding: 0 16px 0 22px;
    justify-content: space-between;
  }

  .sidebar-shell.is-collapsed .brand-name,
  .sidebar-shell.is-collapsed .sidebar-toggle,
  .sidebar-shell.is-collapsed .nav-section-label,
  .sidebar-shell.is-collapsed .nav-item-label,
  .sidebar-shell.is-collapsed .nav-item-indicator,
  .sidebar-shell.is-collapsed .sidebar-footer-button span {
    display: inline;
  }

  .sidebar-shell.is-collapsed .sidebar-toggle {
    display: inline-flex;
  }

  .sidebar-shell.is-collapsed .nav-section-label,
  .sidebar-shell.is-collapsed .nav-item-label,
  .sidebar-shell.is-collapsed .nav-item-indicator {
    display: initial;
  }

  .sidebar-shell.is-collapsed .sidebar-scroll {
    padding-right: 12px;
    padding-left: 12px;
  }

  .sidebar-shell.is-collapsed .nav-section {
    display: block;
  }

  .sidebar-shell.is-collapsed .nav-item {
    justify-content: flex-start;
    padding: 0 12px;
  }

  .sidebar-shell.is-collapsed .nav-item-icon {
    width: 20px;
  }

  .sidebar-shell.is-collapsed .sidebar-footer-button {
    justify-content: flex-start;
    padding: 0 10px;
  }
}
</style>
