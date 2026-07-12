import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import vuetify from "./plugins/vuetify";
import "animate.css";
import "./assets/css/index.css";
import "./assets/css/iconfont.css";
import "./assets/css/markdown.css";
import config from "./assets/js/config";
import { loadSocialSdk } from "./plugins/socialSdk";
import dayjs from "dayjs";
import axios from "axios";
import InfiniteLoading from "v3-infinite-loading";
import "v3-infinite-loading/lib/style.css";
import "highlight.js/styles/atom-one-dark.css";
import Toast from "./components/toast/index";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

const app = createApp(App);
loadSocialSdk(config);
axios.defaults.timeout = 15000;
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = "XSRF-TOKEN";
axios.defaults.xsrfHeaderName = "X-XSRF-TOKEN";
axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
app.config.globalProperties.config = config;
app.config.globalProperties.axios = axios;
app.config.globalProperties.date = value => dayjs(value).format("YYYY-MM-DD");
app.config.globalProperties.year = value => dayjs(value).format("YYYY");
app.config.globalProperties.hour = value => dayjs(value).format("HH:mm:ss");
app.config.globalProperties.time = value => dayjs(value).format("YYYY-MM-DD HH:mm:ss");
app.config.globalProperties.num = value => value >= 1000 ? (value / 1000).toFixed(1) + "k" : value;
app.config.globalProperties.$imagePreview = ({ images, index = 0 }) => {
  window.dispatchEvent(new CustomEvent("image-preview", { detail: { images, index } }));
};
app.component("InfiniteLoading", InfiniteLoading);
app.use(store).use(router).use(vuetify).use(Toast);

router.beforeEach((to, from, next) => {
  NProgress.start();
  if (to.meta.title) document.title = to.meta.title;
  next();
});
router.afterEach(() => {
  window.scrollTo({ top: 0, behavior: "instant" });
  NProgress.done();
});
axios.interceptors.response.use(
  response => {
    if (response.data.code === 40001) {
      app.config.globalProperties.$toast({ type: "error", message: response.data.message });
      app.config.globalProperties.$store?.commit("logout");
    } else if (response.data.code === 40300) {
      app.config.globalProperties.$toast({ type: "error", message: response.data.message });
    } else if (response.data.code === 50000) {
      app.config.globalProperties.$toast({ type: "error", message: "系统异常，请稍后重试" });
    }
    return response;
  },
  error => {
    const status = error.response?.status;
    const serverMessage = typeof error.response?.data?.message === "string" ? error.response.data.message : "";
    const message = serverMessage || (status === 401
      ? "登录已过期，请重新登录"
      : status === 403
        ? "没有执行此操作的权限"
        : status === 429
          ? "操作过于频繁，请稍后重试"
          : status === 503
            ? "服务暂时不可用，请稍后重试"
            : error.code === "ECONNABORTED"
              ? "请求超时，请检查网络后重试"
              : "网络请求失败，请稍后重试");
    app.config.globalProperties.$toast({ type: "error", message });
    return Promise.reject(error);
  }
);
app.mount("#app");
