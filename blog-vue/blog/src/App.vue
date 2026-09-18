<template>
  <v-app id="app" class="site-app">
    <TopNavBar></TopNavBar>
    <SideNavBar></SideNavBar>
    <v-main class="site-main">
      <router-view :key="$route.fullPath" />
    </v-main>
    <BlogFooter></BlogFooter>
    <BackTop></BackTop>
    <SearchModel></SearchModel>
    <ImagePreview />
  </v-app>
</template>

<script>
import TopNavBar from "./components/layout/TopNavBar";
import SideNavBar from "./components/layout/SideNavBar";
import BlogFooter from "./components/layout/Footer";
import BackTop from "./components/BackTop";
import SearchModel from "./components/model/SearchModel";
import ImagePreview from "./components/ImagePreview";

export default {
  created() {
    this.getBlogInfo();
  },
  components: {
    TopNavBar,
    SideNavBar,
    BlogFooter,
    BackTop,
    SearchModel,
    ImagePreview
  },
  methods: {
    async getBlogInfo() {
      const retryDelays = [0, 800, 1600];
      for (const delay of retryDelays) {
        if (delay) await new Promise(resolve => setTimeout(resolve, delay));
        try {
          const data = await this.$api.public.home({
            suppressErrorToast: true,
            timeout: 5000
          });
          if (data.data) this.$store.commit("checkBlogInfo", data.data);
          return;
        } catch {
          // Keep the safe defaults while retrying a transient edge failure.
        }
      }
    }
  },
  computed: {}
};
</script>
