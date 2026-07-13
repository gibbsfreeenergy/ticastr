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
import { installHttp } from "./api/http";
import { installSafeHtml } from "./plugins/safeHtml";
import InfiniteLoading from "v3-infinite-loading";
import "v3-infinite-loading/lib/style.css";
import Toast from "./components/toast/index";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

const app = createApp(App);
loadSocialSdk(config);
app.config.globalProperties.config = config;
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
installHttp(app, store);
installSafeHtml(app);

router.beforeEach((to, from, next) => {
  NProgress.start();
  if (to.meta.title) document.title = to.meta.title;
  next();
});
router.afterEach(() => {
  window.scrollTo({ top: 0, behavior: "instant" });
  NProgress.done();
});
app.mount("#app");
