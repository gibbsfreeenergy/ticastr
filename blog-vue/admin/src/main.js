import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from "element-plus";
import "element-plus/es/components/button/style/css";
import "element-plus/es/components/form/style/css";
import "element-plus/es/components/input/style/css";
import "./assets/css/index.css";
import "./assets/css/iconfont.css";
import config from "./assets/js/config";
import { installHttp } from "./api/http";
import { installSafeHtml } from "./plugins/safeHtml";
import { installImageFallback } from "./plugins/imageFallback";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import dayjs from "dayjs";
import { generaMenu, isMenuReady, resetMenuLoader } from "./assets/js/menu";
import { resetRouter } from "./router";

const app = createApp(App);
let adminElementComponentsPromise;

function registerLoginElementComponents() {
  [ElButton, ElForm, ElFormItem, ElInput].forEach(component => app.component(component.name, component));
}

function ensureAdminElementComponents() {
  if (!adminElementComponentsPromise) {
    adminElementComponentsPromise = import("./plugins/elementPlus").then(({ registerAdminElementComponents }) => {
      registerAdminElementComponents(app);
    });
  }
  return adminElementComponentsPromise;
}
app.config.globalProperties.config = config;
app.config.globalProperties.$message = ElMessage;
app.config.globalProperties.$moment = dayjs;
app.config.globalProperties.date = (value, formatStr = "YYYY-MM-DD") => dayjs(value).format(formatStr);
app.config.globalProperties.dateTime = (value, formatStr = "YYYY-MM-DD HH:mm:ss") => dayjs(value).format(formatStr);
registerLoginElementComponents();
app.use(store).use(router);
installHttp(app);
installSafeHtml(app);
installImageFallback();

NProgress.configure({ easing: "ease", speed: 500, showSpinner: false, trickleSpeed: 200, minimum: 0.3 });
router.beforeEach(async to => {
  NProgress.start();
  if (to.path === "/login") {
    return true;
  }
  if (!store.state.userId) {
    return { path: "/login", replace: true };
  }
  try {
    await ensureAdminElementComponents();
    const shouldResolveAgain = !isMenuReady();
    await generaMenu();
    return shouldResolveAgain ? { path: to.fullPath, replace: true } : true;
  } catch (error) {
    store.commit("logout");
    resetRouter();
    resetMenuLoader();
    return { path: "/login", replace: true };
  }
});
router.afterEach(() => NProgress.done());

app.mount("#app");
