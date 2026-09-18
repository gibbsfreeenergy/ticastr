<template>
  <v-navigation-drawer
    v-model="drawer"
    width="380"
    temporary
    location="right"
    class="site-drawer"
    scrim
  >
    <div class="drawer-shell">
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
    }
  }
};
</script>

<style scoped>
.site-drawer {
  width: min(380px, calc(100vw - 18px)) !important;
  overflow: hidden;
  border-left: 1px solid var(--glass-border) !important;
  border-radius: 30px 0 0 30px;
  background: var(--glass-surface) !important;
  box-shadow: -28px 0 74px rgba(24, 32, 43, 0.22);
  -webkit-backdrop-filter: blur(28px) saturate(175%);
  backdrop-filter: blur(28px) saturate(175%);
}

.site-drawer :deep(.v-navigation-drawer__content) {
  overflow-y: auto;
}

.drawer-shell {
  position: relative;
  min-height: 100%;
  padding: 30px 26px 36px;
  background:
    radial-gradient(circle at 12% 4%, rgba(255, 255, 255, 0.5), transparent 32%),
    linear-gradient(160deg, rgba(255, 255, 255, 0.14), transparent 45%),
    color-mix(in srgb, var(--glass-surface) 72%, transparent);
}

.drawer-shell::after {
  position: absolute;
  inset: 1px 0 0;
  border-radius: 30px 0 0 30px;
  box-shadow: inset 1px 0 0 rgba(255, 255, 255, 0.36), inset 0 1px 0 rgba(255, 255, 255, 0.35);
  content: "";
  pointer-events: none;
}

.drawer-heading {
  position: relative;
  z-index: 1;
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
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--glass-border);
  border-radius: 50%;
  color: var(--muted);
  background: var(--glass-surface-strong);
  font-size: 28px;
  font-weight: 300;
  line-height: 1;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.drawer-close:hover {
  color: var(--ink);
  background: var(--glass-highlight);
  transform: rotate(8deg);
}

.blogger-info {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 40px;
  padding: 18px;
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  background: var(--glass-surface-strong);
  box-shadow: inset 0 1px 0 var(--glass-highlight), 0 12px 30px rgba(24, 32, 43, 0.08);
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
  position: relative;
  z-index: 1;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 18px 0 0;
  padding: 14px 4px;
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
  position: relative;
  z-index: 1;
  display: grid;
  gap: 10px;
  margin-top: 22px;
}

.drawer-menu a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 14px;
  border: 1px solid transparent;
  border-radius: 18px;
  color: var(--muted);
  background: color-mix(in srgb, var(--glass-surface-strong) 58%, transparent);
  font-size: 19px;
  transition: color 180ms ease, border-color 180ms ease, background 180ms ease, transform 180ms ease;
}

.drawer-menu a span:last-child {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 12px;
  opacity: 0.45;
}

.drawer-menu a:hover,
.drawer-menu a.active {
  border-color: color-mix(in srgb, var(--sage) 72%, var(--glass-border));
  color: var(--sage-deep);
  background: color-mix(in srgb, var(--sage) 16%, var(--glass-surface-strong));
  box-shadow: inset 0 1px 0 var(--glass-highlight);
  transform: translateX(-2px);
}

.drawer-note {
  position: relative;
  z-index: 1;
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
    width: min(360px, calc(100vw - 18px)) !important;
    border-radius: 30px 0 0 30px;
  }

  .drawer-shell {
    padding: 24px 20px calc(26px + env(safe-area-inset-bottom));
  }

  .drawer-heading {
    margin-bottom: 0;
  }

  .drawer-close {
    width: 34px;
    height: 34px;
  }

  .blogger-info {
    gap: 13px;
    margin-top: 32px;
    padding: 16px;
  }

  .blogger-info :deep(.v-avatar) {
    flex-basis: 64px;
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
    margin-top: 16px;
    padding: 12px 4px;
  }

  .drawer-stat strong {
    font-size: 19px;
  }

  .drawer-menu {
    gap: 8px;
    margin-top: 18px;
  }

  .drawer-menu a,
  .drawer-menu a:hover,
  .drawer-menu a.active {
    min-height: 62px;
    align-items: center;
    flex-direction: row;
    justify-content: space-between;
    padding: 14px;
    border-color: transparent;
    border-radius: 18px;
    background: color-mix(in srgb, var(--glass-surface-strong) 58%, transparent);
    color: var(--muted);
  }

  .drawer-menu a.active {
    border-color: color-mix(in srgb, var(--sage) 72%, var(--glass-border));
    background: color-mix(in srgb, var(--sage) 16%, var(--glass-surface-strong));
    color: var(--sage-deep);
  }

  .drawer-menu a span:last-child {
    color: var(--muted-light);
    font-size: 11px;
  }

  .drawer-note {
    margin-top: 42px;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .site-drawer {
    background: var(--paper-strong) !important;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}
</style>
