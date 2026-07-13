import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from "element-plus";
import "element-plus/es/components/button/style/css";
import "element-plus/es/components/form/style/css";
import "element-plus/es/components/input/style/css";
import "element-plus/es/components/message/style/css";
import "./assets/css/index.css";
import "./assets/css/iconfont.css";
import config from "./assets/js/config";
import axios from "axios";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import dayjs from "dayjs";

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
axios.defaults.timeout = 15000;
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = "XSRF-TOKEN";
axios.defaults.xsrfHeaderName = "X-XSRF-TOKEN";
axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
app.config.globalProperties.config = config;
app.config.globalProperties.axios = axios;
app.config.globalProperties.$moment = dayjs;
app.config.globalProperties.date = (value, formatStr = "YYYY-MM-DD") => dayjs(value).format(formatStr);
app.config.globalProperties.dateTime = (value, formatStr = "YYYY-MM-DD HH:mm:ss") => dayjs(value).format(formatStr);
registerLoginElementComponents();
app.use(store).use(router);

NProgress.configure({ easing: "ease", speed: 500, showSpinner: false, trickleSpeed: 200, minimum: 0.3 });
router.beforeEach(async (to, from, next) => {
  NProgress.start();
  if (to.path !== "/login" && store.state.userId) {
    await ensureAdminElementComponents();
  }
  next(to.path === "/login" || store.state.userId ? undefined : { path: "/login" });
});
router.afterEach(() => NProgress.done());

axios.interceptors.response.use(
  response => {
    if (response.data.code === 40001) {
      ElMessage.error(response.data.message);
      router.push({ path: "/login" });
    } else if (response.data.code === 50000) {
      ElMessage.error(response.data.message);
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
    ElMessage.error(message);
    if (status === 401) {
      store.commit("logout");
      router.push({ path: "/login" });
    }
    return Promise.reject(error);
  }
);

app.mount("#app");
