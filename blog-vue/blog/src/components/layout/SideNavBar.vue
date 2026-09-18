<template>
  <v-navigation-drawer
    v-model="drawer"
    width="320"
    temporary
    :location="drawerLocation"
    class="site-drawer"
    scrim
  >
    <div class="drawer-shell">
      <span class="drawer-handle" aria-hidden="true" />
      <div class="drawer-heading">
        <span class="drawer-kicker">NAVIGATION</span>
        <button class="drawer-close" type="button" aria-label="关闭菜单" @click="drawer = false">×</button>
      </div>

      <div class="blogger-info">
        <v-avatar size="92">
          <img
            v-if="blogInfo.websiteConfig.websiteAvatar"
            :src="avatar"
            :alt="blogInfo.websiteConfig.websiteAuthor"
          />
        </v-avatar>
        <div class="blogger-copy">
          <strong>{{ blogInfo.websiteConfig.websiteAuthor }}</strong>
          <span>{{ blogInfo.websiteConfig.websiteIntro }}</span>
        </div>
      </div>

      <div class="drawer-stat">
        <span>文章</span>
        <strong>{{ blogInfo.articleCount }}</strong>
      </div>

      <nav class="drawer-menu" aria-label="移动端主导航">
        <router-link to="/" :class="{ active: $route.path === '/' }" @click="drawer = false">
          <span>首页</span><span>01</span>
        </router-link>
        <router-link to="/archives" :class="{ active: $route.path === '/archives' }" @click="drawer = false">
          <span>归档</span><span>02</span>
        </router-link>
        <router-link to="/about" :class="{ active: $route.path === '/about' }" @click="drawer = false">
          <span>关于</span><span>03</span>
        </router-link>
      </nav>

      <div class="drawer-note">
        <span class="drawer-note-line" />
        <p>慢一点，也很好。</p>
        <small>在这里，记录生活，分享技术。</small>
      </div>
    </div>
  </v-navigation-drawer>
</template>

<script>
import { normalizeMediaUrl } from "../../utils/media";

export default {
  data() {
    return {
      viewportWidth: typeof window === "undefined" ? 0 : window.innerWidth
    };
  },
  mounted() {
    window.addEventListener("resize", this.updateViewport, { passive: true });
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.updateViewport);
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    },
    avatar() {
      return normalizeMediaUrl(this.blogInfo.websiteConfig.websiteAvatar);
    },
    drawer: {
      set(value) {
        this.$store.state.drawer = value;
      },
      get() {
        return this.$store.state.drawer;
      }
    },
    drawerLocation() {
      return this.viewportWidth <= 760 ? "bottom" : "right";
    }
  },
  methods: {
    updateViewport() {
      this.viewportWidth = window.innerWidth;
    }
  }
};
</script>

<style scoped>
.site-drawer :deep(.v-navigation-drawer__content) {
  overflow-y: auto;
}

.drawer-shell {
  min-height: 100%;
  padding: 28px 28px 34px;
  background: var(--paper-strong);
}

.drawer-handle {
  display: none;
}

.drawer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-kicker {
  color: var(--muted);
  font-size: 10px;
  letter-spacing: 0.2em;
}

.drawer-close {
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 28px;
  font-weight: 300;
  line-height: 1;
}

.blogger-info {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 56px 10px 30px;
}

.blogger-info :deep(.v-avatar) {
  flex: 0 0 92px;
  overflow: hidden;
}

.blogger-info :deep(.v-avatar img) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.blogger-copy {
  min-width: 0;
}

.blogger-copy strong,
.blogger-copy span {
  display: block;
}

.blogger-copy strong {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22px;
  font-weight: 500;
}

.blogger-copy span {
  margin-top: 3px;
  color: var(--muted);
  font-size: 13px;
}

.drawer-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 16px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}

.drawer-stat strong {
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22px;
  font-weight: 500;
}

.drawer-menu {
  display: grid;
  gap: 4px;
  margin-top: 26px;
}

.drawer-menu a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 10px;
  border-bottom: 1px solid transparent;
  color: var(--muted);
  font-size: 19px;
  transition: color 180ms ease, border-color 180ms ease, padding 180ms ease;
}

.drawer-menu a span:last-child {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 12px;
  opacity: 0.45;
}

.drawer-menu a:hover,
.drawer-menu a.active {
  padding-left: 18px;
  border-color: var(--sage);
  color: var(--sage-deep);
}

.drawer-note {
  margin-top: 74px;
  color: var(--muted);
}

.drawer-note-line {
  display: block;
  width: 34px;
  height: 2px;
  margin-bottom: 16px;
  background: var(--blush);
}

.drawer-note p {
  margin: 0 0 4px !important;
  color: var(--ink);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22px;
}

.drawer-note small {
  font-size: 13px;
}

@media (max-width: 760px) {
  .site-drawer {
    width: 100% !important;
    max-height: min(76vh, 620px);
    border-radius: 28px 28px 0 0;
    box-shadow: 0 -22px 60px rgba(24, 32, 43, 0.2);
    overflow: hidden;
  }

  .site-drawer :deep(.v-navigation-drawer__content) {
    overflow-y: auto;
  }

  .drawer-shell {
    min-height: auto;
    padding: 12px 20px calc(20px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: color-mix(in srgb, var(--paper-strong) 94%, var(--sage) 6%);
  }

  .drawer-handle {
    display: block;
    width: 42px;
    height: 4px;
    margin: 0 auto 15px;
    border-radius: 999px;
    background: var(--line-strong);
  }

  .drawer-heading {
    margin-bottom: 18px;
  }

  .drawer-close {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border: 1px solid var(--line);
    border-radius: 50%;
    font-size: 22px;
  }

  .blogger-info {
    gap: 12px;
    padding: 0 0 18px;
  }

  .blogger-info :deep(.v-avatar) {
    flex-basis: 58px;
  }

  .blogger-copy strong {
    font-size: 20px;
  }

  .blogger-copy span {
    overflow: hidden;
    max-width: 250px;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .drawer-stat {
    padding: 12px 0;
  }

  .drawer-stat strong {
    font-size: 19px;
  }

  .drawer-menu {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin-top: 16px;
  }

  .drawer-menu a,
  .drawer-menu a:hover,
  .drawer-menu a.active {
    min-height: 74px;
    align-items: flex-start;
    flex-direction: column;
    justify-content: space-between;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 16px;
    background: color-mix(in srgb, var(--paper) 74%, transparent);
    color: var(--muted);
  }

  .drawer-menu a.active {
    border-color: rgba(143, 169, 154, 0.7);
    background: rgba(143, 169, 154, 0.16);
    color: var(--sage-deep);
  }

  .drawer-menu a span:last-child {
    color: var(--muted-light);
    font-size: 11px;
  }

  .drawer-note {
    display: none;
  }
}
</style>
