<template>
  <v-navigation-drawer
    v-model="drawer"
    width="320"
    temporary
    location="right"
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
            :src="blogInfo.websiteConfig.websiteAvatar"
            :alt="blogInfo.websiteConfig.websiteAuthor"
          />
        </v-avatar>
        <strong>{{ blogInfo.websiteConfig.websiteAuthor }}</strong>
        <span>{{ blogInfo.websiteConfig.websiteIntro }}</span>
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
export default {
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
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
.drawer-shell {
  min-height: 100%;
  padding: 28px 28px 34px;
  background: var(--paper-strong);
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
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 64px 10px 30px;
  text-align: center;
}

.blogger-info strong {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22px;
  font-weight: 500;
}

.blogger-info span {
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
</style>
