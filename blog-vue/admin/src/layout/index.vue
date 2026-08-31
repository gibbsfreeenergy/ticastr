<template>
  <div class="admin-shell">
    <SideBar :mobile-open="mobileOpen" @close="mobileOpen = false" />
    <div class="app-content" :class="{ 'is-collapsed': isCollapsed }">
      <NavBar @open-mobile="mobileOpen = true" />
      <main class="app-main">
        <div class="fade-transform-box">
          <transition name="fade-transform" mode="out-in">
            <router-view :key="$route.fullPath" />
          </transition>
        </div>
      </main>
    </div>
  </div>
</template>

<script>
import NavBar from "./components/NavBar.vue";
import SideBar from "./components/SideBar.vue";
export default {
  name: "AdminLayout",
  components: {
    NavBar,
    SideBar
  },
  data() {
    return {
      mobileOpen: false
    };
  },
  computed: {
    isCollapsed() {
      return this.$store.state.collapse;
    }
  },
  watch: {
    $route() {
      this.mobileOpen = false;
    }
  }
};
</script>

<style scoped>
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: opacity 240ms ease, transform 360ms cubic-bezier(0.22, 1, 0.36, 1);
}
.fade-transform-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
.fade-transform-box {
  position: relative;
  width: 100%;
  overflow: hidden;
}

@media (prefers-reduced-motion: reduce) {
  .fade-transform-enter-active,
  .fade-transform-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>
