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
import { installImageFallback } from "./plugins/imageFallback";
import Toast from "./components/toast/index";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { applySeo } from "./utils/seo";

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
app.use(store).use(router).use(vuetify).use(Toast);
installHttp(app, store);
installSafeHtml(app);
installImageFallback();

router.beforeEach(to => {
  NProgress.start();
  if (to.meta.title) document.title = to.meta.title;
  applySeo({
    articleTitle: to.meta.title || "Ticastr",
    articleSummary: "记录生活，分享技术"
  }, { siteName: "Ticastr" });
  return true;
});
router.afterEach(() => {
  window.scrollTo({ top: 0, behavior: "instant" });
  NProgress.done();
});
app.mount("#app");
