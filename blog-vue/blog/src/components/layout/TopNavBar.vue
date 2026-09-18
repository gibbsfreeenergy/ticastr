<template>
  <v-app-bar :class="navClass" hide-on-scroll flat height="60">
    <div class="d-md-none nav-mobile-container">
      <div class="nav-mobile-title">
        <router-link to="/">
          {{ blogInfo.websiteConfig.websiteAuthor }}
        </router-link>
      </div>
      <div class="nav-mobile-actions">
        <a @click="openSearch"><i class="iconfont iconsousuo"/></a>
        <a class="nav-mobile-menu" @click="openDrawer">
          <i class="iconfont iconhanbao" />
        </a>
      </div>
    </div>
    <div class="d-md-block d-none nav-container">
      <div class="float-left blog-title">
        <router-link to="/">
          {{ blogInfo.websiteConfig.websiteAuthor }}
        </router-link>
      </div>
      <div class="float-right nav-title">
        <div class="menus-item">
          <a class="menu-btn" @click="openSearch">
            <i class="iconfont iconsousuo" /> 搜索
          </a>
        </div>
        <div class="menus-item">
          <router-link class="menu-btn" to="/">
            <i class="iconfont iconzhuye" /> 首页
          </router-link>
        </div>
        <div class="menus-item">
          <router-link class="menu-btn" to="/archives">
            <i class="iconfont iconguidang" /> 归档
          </router-link>
        </div>
        <div class="menus-item">
          <router-link class="menu-btn" to="/about">
            <i class="iconfont iconzhifeiji" /> 关于
          </router-link>
        </div>
      </div>
    </div>
  </v-app-bar>
</template>

<script>
export default {
  mounted() {
    this.updateNavigation();
    window.addEventListener("scroll", this.updateNavigation, { passive: true });
  },
  beforeUnmount() {
    window.removeEventListener("scroll", this.updateNavigation);
  },
  data: function() {
    return {
      navClass: "nav"
    };
  },
  watch: {
    "$route.path"() {
      this.updateNavigation();
    }
  },
  methods: {
    updateNavigation() {
      const scrollTop =
        window.pageYOffset ||
        document.documentElement.scrollTop ||
        document.body.scrollTop;
      this.navClass = scrollTop > 60 ? "nav-fixed" : "nav";
    },
    openSearch() {
      this.$store.state.searchFlag = true;
    },
    openDrawer() {
      this.$store.state.drawer = true;
    }
  },
  computed: {
    blogInfo() {
      return this.$store.state.blogInfo;
    }
  }
};
</script>

<style scoped>
:global(.v-app-bar .v-toolbar__content) {
  overflow: visible !important;
}

i {
  margin-right: 4px;
}
ul {
  list-style: none;
}
.nav {
  background: rgba(0, 0, 0, 0) !important;
}
.nav a {
  color: #eee !important;
}
.nav .menu-btn {
  text-shadow: 0.05rem 0.05rem 0.1rem rgba(0, 0, 0, 0.3);
}
.nav .blog-title a {
  text-shadow: 0.1rem 0.1rem 0.2rem rgba(0, 0, 0, 0.15);
}
.v-theme--light.nav-fixed {
  background: rgba(255, 255, 255, 0.8) !important;
  box-shadow: 0 5px 6px -5px rgba(133, 133, 133, 0.6);
}
.v-theme--dark.nav-fixed {
  background: rgba(18, 18, 18, 0.8) !important;
}
.v-theme--dark.nav-fixed a {
  color: rgba(255, 255, 255, 0.8) !important;
}
.v-theme--light.nav-fixed a {
  color: #4c4948 !important;
}
.nav-fixed .menus-item a,
.nav-fixed .blog-title a {
  text-shadow: none;
}
.nav-container {
  font-size: 14px;
  width: 100%;
  height: 100%;
}
.nav-mobile-container {
  width: 100%;
  display: flex;
  align-items: center;
}
.nav-mobile-title {
  min-width: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: bold;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-mobile-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.nav-mobile-menu {
  font-size: 20px;
}
@media (min-width: 960px) {
  .nav-mobile-container {
    display: none !important;
  }
  .nav-container {
    display: block !important;
  }
}
@media (max-width: 959.98px) {
  .nav-mobile-container {
    display: flex !important;
  }
  .nav-container {
    display: none !important;
  }
}
.blog-title,
.nav-title {
  display: flex;
  align-items: center;
  height: 100%;
}
.blog-title a {
  font-size: 18px;
  font-weight: bold;
}
.menus-item {
  position: relative;
  display: inline-block;
  margin: 0 0 0 0.875rem;
}
.menus-item a {
  transition: all 0.2s;
}
.nav-fixed .menu-btn:hover {
  color: #49b1f5 !important;
}
.menu-btn:hover:after {
  width: 100%;
}
.menus-item a:after {
  position: absolute;
  bottom: -5px;
  left: 0;
  z-index: -1;
  width: 0;
  height: 3px;
  background-color: #80c8f8;
  content: "";
  transition: all 0.3s ease-in-out;
}
</style>
