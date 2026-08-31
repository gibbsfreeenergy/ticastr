<template>
  <div class="navigation-stack">
    <header class="topbar">
      <div class="topbar-leading">
        <button
          class="mobile-menu-button"
          type="button"
          aria-label="打开导航"
          title="打开导航"
          @click="$emit('open-mobile')"
        >
          <AppIcon name="menu" :size="19" />
        </button>
        <div class="breadcrumb-wrap">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbList" :key="`${item.path}-${item.name}`">
              <router-link v-if="item.path !== $route.path" :to="item.path">{{ item.name }}</router-link>
              <span v-else>{{ item.name }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
      </div>

      <div class="topbar-actions">
        <button
          class="command-trigger"
          type="button"
          aria-label="搜索页面"
          @click="openSearch"
        >
          <AppIcon name="search" :size="17" />
          <span>搜索</span>
          <kbd>⌘ K</kbd>
        </button>
        <button
          class="topbar-icon-button"
          type="button"
          :aria-label="fullscreen ? '退出全屏' : '进入全屏'"
          :title="fullscreen ? '退出全屏' : '进入全屏'"
          @click="fullScreen"
        >
          <AppIcon name="fullscreen" :size="18" />
        </button>
        <el-dropdown class="user-dropdown" trigger="click" @command="handleCommand">
          <button class="user-trigger" type="button" aria-label="打开用户菜单">
            <el-avatar :size="34" :src="avatar || undefined">{{ avatarLabel }}</el-avatar>
            <span class="user-copy">
              <strong>{{ displayName }}</strong>
              <small>管理员</small>
            </span>
            <AppIcon name="chevronDown" :size="15" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="setting">
                <AppIcon name="settings" :size="15" />
                <span>个人中心</span>
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <AppIcon name="close" :size="15" />
                <span>退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="tabs-bar">
      <div class="tabs-scroll" role="tablist" aria-label="已打开页面">
        <button
          v-for="tab in $store.state.tabList"
          :key="tab.path"
          class="tab-item"
          :class="{ 'is-active': isActive(tab) }"
          type="button"
          role="tab"
          :aria-selected="isActive(tab)"
          @click="goTo(tab)"
        >
          <span class="tab-dot" aria-hidden="true" />
          <span class="tab-name">{{ tab.name }}</span>
          <span
            v-if="tab.path !== '/'"
            class="tab-close"
            role="button"
            tabindex="0"
            aria-label="关闭页面"
            @click.stop="removeTab(tab)"
            @keydown.enter.stop="removeTab(tab)"
          >
            <AppIcon name="close" :size="13" />
          </span>
        </button>
      </div>
      <button class="clear-tabs-button" type="button" @click="closeAllTab">
        <span>全部关闭</span>
        <AppIcon name="close" :size="14" />
      </button>
    </div>

    <div v-if="isSearchOpen" class="command-scrim" @click.self="closeSearch">
      <section class="command-palette" role="dialog" aria-modal="true" aria-label="搜索页面">
        <div class="command-input-wrap">
          <AppIcon name="search" :size="19" />
          <input
            ref="searchInput"
            v-model="searchKeyword"
            type="search"
            autocomplete="off"
            placeholder="搜索页面或菜单"
            aria-label="搜索页面或菜单"
            @keydown="handleSearchKeydown"
          />
          <button type="button" aria-label="关闭搜索" @click="closeSearch">
            <span>Esc</span>
          </button>
        </div>
        <div class="command-results">
          <p v-if="filteredMenuItems.length === 0" class="command-empty">没有找到匹配页面</p>
          <button
            v-for="(item, index) in filteredMenuItems"
            :key="item.path"
            class="command-result"
            :class="{ 'is-highlighted': activeCommandIndex === index }"
            type="button"
            :aria-selected="activeCommandIndex === index"
            @click="selectSearchResult(item)"
          >
            <span class="command-result-icon"><AppIcon :name="iconFor(item)" :size="17" /></span>
            <span class="command-result-copy">
              <strong>{{ item.name }}</strong>
              <small>{{ item.path }}</small>
            </span>
            <AppIcon name="chevronRight" :size="16" />
          </button>
        </div>
        <div class="command-footer">
          <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
          <span><kbd>↵</kbd> 打开</span>
          <span><kbd>Esc</kbd> 关闭</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import { resetRouter } from "../../router";
import { resetMenuLoader } from "../../assets/js/menu";
import AppIcon from "../../components/AppIcon.vue";

export default {
  name: "NavBar",
  components: { AppIcon },
  emits: ["open-mobile"],
  data() {
    return {
      fullscreen: false,
      isSearchOpen: false,
      searchKeyword: "",
      activeCommandIndex: 0
    };
  },
  computed: {
    breadcrumbList() {
      let matched = this.$route.matched.filter(item => item.name);
      const first = matched[0];
      if (first && first.name !== "首页") {
        matched = [{ path: "/", name: "首页" }].concat(matched);
      }
      return matched;
    },
    displayName() {
      return this.$store.state.nickname || "管理员";
    },
    avatar() {
      return this.$store.state.avatar;
    },
    avatarLabel() {
      return this.displayName.slice(0, 1).toUpperCase();
    },
    menuItems() {
      return this.flattenMenu(this.$store.state.userMenuList);
    },
    filteredMenuItems() {
      const keyword = this.searchKeyword.trim().toLowerCase();
      if (!keyword) return this.menuItems;
      return this.menuItems.filter(item => `${item.name} ${item.path}`.toLowerCase().includes(keyword));
    }
  },
  created() {
    this.saveCurrentTab();
  },
  mounted() {
    document.addEventListener("fullscreenchange", this.syncFullscreen);
    window.addEventListener("keydown", this.commandKeydownHandler);
    this.syncFullscreen();
  },
  beforeUnmount() {
    document.removeEventListener("fullscreenchange", this.syncFullscreen);
    window.removeEventListener("keydown", this.commandKeydownHandler);
  },
  watch: {
    $route() {
      this.saveCurrentTab();
    },
    searchKeyword() {
      this.activeCommandIndex = 0;
    }
  },
  methods: {
    normalizePath(path, parentPath = "/") {
      if (!path || path === "home") return parentPath || "/";
      if (path.startsWith("/")) return path;
      if (!parentPath || parentPath === "/") return `/${path}`;
      return `${parentPath.replace(/\/$/, "")}/${path}`;
    },
    flattenMenu(routes, parentPath = "/") {
      const items = [];
      (routes || []).forEach(route => {
        const routePath = this.normalizePath(route.path, parentPath);
        const children = Array.isArray(route.children) ? route.children : [];
        if (children.length > 0) {
          items.push(...this.flattenMenu(children, routePath));
        } else if (!route.hidden && route.name) {
          items.push({ ...route, path: routePath });
        }
      });
      return items.filter((item, index, list) => list.findIndex(candidate => candidate.path === item.path) === index);
    },
    iconFor(item) {
      const iconMap = {
        首页: "home",
        发布文章: "pen",
        文章列表: "file",
        分类管理: "folder",
        标签管理: "tag",
        评论管理: "comment",
        留言管理: "message",
        用户列表: "users",
        在线用户: "user",
        角色管理: "shield",
        接口管理: "code",
        菜单管理: "list",
        网站管理: "globe",
        页面管理: "file",
        友链管理: "link",
        关于我: "info",
        相册列表: "image",
        发布说说: "bubble",
        说说列表: "list",
        操作日志: "history",
        个人中心: "settings"
      };
      return iconMap[item.name] || "grid";
    },
    isActive(tab) {
      return tab.path === this.$route.path;
    },
    saveCurrentTab() {
      if (this.$route.name) this.$store.commit("saveTab", this.$route);
    },
    goTo(tab) {
      this.$router.push({ path: tab.path });
    },
    removeTab(tab) {
      this.$store.commit("removeTab", tab);
      if (tab.path === this.$route.path) {
        const tabList = this.$store.state.tabList;
        const fallback = tabList[tabList.length - 1] || { path: "/" };
        this.$router.push({ path: fallback.path });
      }
    },
    closeAllTab() {
      this.$store.commit("resetTab");
      this.$router.push({ path: "/" });
    },
    handleCommand(command) {
      if (command === "setting") {
        this.$router.push({ path: "/setting" });
        return;
      }
      if (command === "logout") {
        this.$api.auth.logout().catch(() => {});
        this.$store.commit("logout");
        this.$store.commit("resetTab");
        resetRouter();
        resetMenuLoader();
        this.$router.push({ path: "/login" });
      }
    },
    syncFullscreen() {
      this.fullscreen = Boolean(document.fullscreenElement);
    },
    async fullScreen() {
      const element = document.documentElement;
      try {
        if (this.fullscreen) {
          if (document.exitFullscreen) await document.exitFullscreen();
          else if (document.webkitCancelFullScreen) document.webkitCancelFullScreen();
          else if (document.mozCancelFullScreen) document.mozCancelFullScreen();
          else if (document.msExitFullscreen) document.msExitFullscreen();
        } else if (element.requestFullscreen) {
          await element.requestFullscreen();
        } else if (element.webkitRequestFullScreen) {
          element.webkitRequestFullScreen();
        } else if (element.mozRequestFullScreen) {
          element.mozRequestFullScreen();
        } else if (element.msRequestFullscreen) {
          element.msRequestFullscreen();
        }
      } catch (error) {
        this.$message.warning("当前浏览器不支持全屏操作");
      }
    },
    commandKeydownHandler(event) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        this.openSearch();
      }
      if (event.key === "Escape" && this.isSearchOpen) this.closeSearch();
    },
    openSearch() {
      this.isSearchOpen = true;
      this.searchKeyword = "";
      this.activeCommandIndex = 0;
      this.$nextTick(() => this.$refs.searchInput?.focus());
    },
    closeSearch() {
      this.isSearchOpen = false;
      this.searchKeyword = "";
    },
    handleSearchKeydown(event) {
      if (event.key === "Escape") {
        this.closeSearch();
        return;
      }
      if (event.key === "ArrowDown") {
        event.preventDefault();
        if (this.filteredMenuItems.length > 0) {
          this.activeCommandIndex = (this.activeCommandIndex + 1) % this.filteredMenuItems.length;
        }
        return;
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        if (this.filteredMenuItems.length > 0) {
          this.activeCommandIndex = (this.activeCommandIndex - 1 + this.filteredMenuItems.length) % this.filteredMenuItems.length;
        }
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        this.selectSearchResult(this.filteredMenuItems[this.activeCommandIndex]);
      }
    },
    selectSearchResult(item) {
      if (!item) return;
      this.$router.push({ path: item.path });
      this.closeSearch();
    }
  }
};
</script>

<style scoped>
.navigation-stack {
  position: relative;
  z-index: 20;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72px;
  gap: 24px;
  padding: 0 32px;
  background: rgba(245, 245, 247, 0.82);
  border-bottom: 1px solid rgba(229, 229, 234, 0.8);
  backdrop-filter: blur(24px) saturate(145%);
}

.topbar-leading,
.topbar-actions,
.user-trigger,
.command-trigger,
.topbar-icon-button {
  display: flex;
  align-items: center;
}

.topbar-leading {
  min-width: 0;
  gap: 18px;
}

.breadcrumb-wrap {
  min-width: 0;
  overflow: hidden;
}

.breadcrumb-wrap :deep(.el-breadcrumb) {
  white-space: nowrap;
}

.breadcrumb-wrap :deep(.el-breadcrumb__inner),
.breadcrumb-wrap :deep(.el-breadcrumb__inner a) {
  color: var(--admin-text-secondary);
  font-size: 13px;
  font-weight: 520;
}

.breadcrumb-wrap :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--admin-text);
  font-weight: 650;
}

.mobile-menu-button,
.topbar-icon-button {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid var(--admin-border);
  border-radius: 11px;
  cursor: pointer;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.mobile-menu-button:hover,
.topbar-icon-button:hover {
  color: var(--admin-blue);
  background: var(--admin-blue-soft);
}

.mobile-menu-button:active,
.topbar-icon-button:active,
.command-trigger:active,
.user-trigger:active,
.clear-tabs-button:active,
.tab-item:active {
  transform: scale(0.97);
}

.topbar-actions {
  flex: 0 0 auto;
  gap: 10px;
}

.command-trigger {
  min-width: 190px;
  height: 38px;
  gap: 9px;
  padding: 0 11px;
  color: var(--admin-text-tertiary);
  font: inherit;
  font-size: 12px;
  text-align: left;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--admin-border);
  border-radius: 11px;
  cursor: pointer;
  transition: color 180ms ease, border-color 180ms ease, background 180ms ease, transform 180ms ease;
}

.command-trigger:hover {
  color: var(--admin-text-secondary);
  background: #fff;
  border-color: #c7c7cc;
}

.command-trigger kbd {
  margin-left: auto;
}

kbd {
  padding: 2px 6px;
  color: var(--admin-text-tertiary);
  font-family: inherit;
  font-size: 10px;
  line-height: 1.35;
  background: var(--admin-bg);
  border: 1px solid var(--admin-border);
  border-radius: 5px;
}

.topbar-icon-button {
  border-color: transparent;
  border-radius: 50%;
}

.user-dropdown {
  margin-left: 4px;
}

.user-trigger {
  gap: 9px;
  padding: 3px 0 3px 3px;
  color: var(--admin-text-secondary);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 12px;
  cursor: pointer;
  transition: color 180ms ease, transform 180ms ease;
}

.user-trigger:hover {
  color: var(--admin-text);
}

.user-trigger :deep(.el-avatar) {
  color: var(--admin-blue);
  font-size: 13px;
  font-weight: 700;
  background: var(--admin-blue-soft);
}

.user-copy {
  display: flex;
  min-width: 74px;
  flex-direction: column;
  gap: 1px;
}

.user-copy strong {
  overflow: hidden;
  color: var(--admin-text);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy small {
  color: var(--admin-text-tertiary);
  font-size: 10px;
}

.tabs-bar {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 14px;
  padding: 0 32px;
  background: rgba(255, 255, 255, 0.62);
  border-bottom: 1px solid var(--admin-border);
}

.tabs-scroll {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  min-height: 30px;
  gap: 7px;
  padding: 0 9px;
  color: var(--admin-text-tertiary);
  font: inherit;
  font-size: 12px;
  font-weight: 520;
  white-space: nowrap;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 9px;
  cursor: pointer;
  transition: color 180ms ease, background 180ms ease, border-color 180ms ease, transform 180ms ease;
}

.tab-item:hover {
  color: var(--admin-text-secondary);
  background: rgba(118, 118, 128, 0.08);
}

.tab-item.is-active {
  color: var(--admin-blue);
  font-weight: 650;
  background: var(--admin-blue-soft);
  border-color: rgba(0, 113, 227, 0.12);
}

.tab-dot {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
  opacity: 0.45;
}

.tab-item.is-active .tab-dot {
  opacity: 1;
}

.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: -4px;
  color: var(--admin-text-tertiary);
  border-radius: 50%;
  cursor: pointer;
}

.tab-close:hover {
  color: var(--admin-text);
  background: rgba(118, 118, 128, 0.14);
}

.clear-tabs-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 6px 0 6px 10px;
  color: var(--admin-text-tertiary);
  font: inherit;
  font-size: 11px;
  background: transparent;
  border: 0;
  border-left: 1px solid var(--admin-border);
  cursor: pointer;
  transition: color 180ms ease, transform 180ms ease;
}

.clear-tabs-button:hover {
  color: var(--admin-text);
}

.command-scrim {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  justify-content: center;
  padding-top: 112px;
  background: rgba(29, 29, 31, 0.2);
  backdrop-filter: blur(9px);
}

.command-palette {
  width: min(580px, calc(100vw - 32px));
  height: fit-content;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(229, 229, 234, 0.95);
  border-radius: 18px;
  box-shadow: 0 24px 80px rgba(29, 29, 31, 0.2);
  animation: palette-in 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.command-input-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 17px 18px;
  color: var(--admin-blue);
  border-bottom: 1px solid var(--admin-border);
}

.command-input-wrap input {
  min-width: 0;
  flex: 1;
  color: var(--admin-text);
  font: inherit;
  font-size: 16px;
  outline: 0;
  background: transparent;
  border: 0;
}

.command-input-wrap input::placeholder {
  color: var(--admin-text-tertiary);
}

.command-input-wrap input::-webkit-search-cancel-button {
  display: none;
}

.command-input-wrap > button {
  padding: 0;
  color: var(--admin-text-tertiary);
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.command-input-wrap > button:hover {
  color: var(--admin-text);
}

.command-results {
  max-height: 360px;
  padding: 8px;
  overflow-y: auto;
}

.command-result {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 11px 12px;
  color: var(--admin-text-secondary);
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 11px;
  cursor: pointer;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.command-result:hover {
  color: var(--admin-text);
  background: var(--admin-blue-soft);
}

.command-result.is-highlighted {
  color: var(--admin-text);
  background: var(--admin-blue-soft);
}

.command-result:active {
  transform: scale(0.99);
}

.command-result-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--admin-blue);
  background: #f2f7ff;
  border-radius: 9px;
}

.command-result-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.command-result-copy strong {
  color: currentColor;
  font-size: 13px;
  font-weight: 620;
}

.command-result-copy small {
  overflow: hidden;
  color: var(--admin-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-empty {
  margin: 0;
  padding: 28px 12px;
  color: var(--admin-text-tertiary);
  font-size: 13px;
  text-align: center;
}

.command-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  color: var(--admin-text-tertiary);
  font-size: 10px;
  border-top: 1px solid var(--admin-border);
}

.command-footer span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

@keyframes palette-in {
  from {
    opacity: 0;
    transform: translateY(-9px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (min-width: 901px) {
  .topbar-icon-button {
    display: inline-flex;
  }
}

@media (max-width: 900px) {
  .topbar {
    min-height: 64px;
    gap: 12px;
    padding: 0 20px;
  }

  .mobile-menu-button {
    display: inline-flex;
  }

  .breadcrumb-wrap :deep(.el-breadcrumb__item:not(:last-child)) {
    display: none;
  }

  .breadcrumb-wrap :deep(.el-breadcrumb__separator) {
    display: none;
  }

  .command-trigger {
    min-width: 38px;
    width: 38px;
    justify-content: center;
    padding: 0;
    border-radius: 50%;
  }

  .command-trigger span,
  .command-trigger kbd,
  .user-copy,
  .user-trigger > :last-child {
    display: none;
  }

  .topbar-icon-button {
    display: inline-flex;
  }

  .user-dropdown {
    margin-left: 0;
  }

  .tabs-bar {
    min-height: 44px;
    gap: 8px;
    padding: 0 20px;
  }

  .clear-tabs-button span {
    display: none;
  }

  .clear-tabs-button {
    padding-left: 8px;
  }

  .command-scrim {
    padding-top: 80px;
  }
}

@media (max-width: 520px) {
  .topbar {
    padding: 0 16px;
  }

  .tabs-bar {
    padding: 0 16px;
  }

  .command-footer {
    gap: 8px;
    justify-content: space-between;
  }
}

@media (prefers-reduced-motion: reduce) {
  .navigation-stack *,
  .navigation-stack *::before,
  .navigation-stack *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
