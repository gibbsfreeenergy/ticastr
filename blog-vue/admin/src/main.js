import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "./assets/css/index.css";
import "./assets/css/iconfont.css";
import config from "./assets/js/config";
import axios from "axios";
import ECharts from "vue-echarts";
import { use } from "echarts/core";
import { SVGRenderer } from "echarts/renderers";
import { LineChart, PieChart, BarChart } from "echarts/charts";
import { TooltipComponent, LegendComponent, TitleComponent, GridComponent } from "echarts/components";
import { MdEditor } from "md-editor-v3";
import "md-editor-v3/lib/style.css";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import dayjs from "dayjs";

use([SVGRenderer, LineChart, PieChart, BarChart, TooltipComponent, LegendComponent, TitleComponent, GridComponent]);

const app = createApp(App);
app.config.globalProperties.config = config;
app.config.globalProperties.axios = axios;
app.config.globalProperties.$moment = dayjs;
app.config.globalProperties.date = (value, formatStr = "YYYY-MM-DD") => dayjs(value).format(formatStr);
app.config.globalProperties.dateTime = (value, formatStr = "YYYY-MM-DD HH:mm:ss") => dayjs(value).format(formatStr);
app.use(store).use(router).use(ElementPlus);
app.component("MdEditor", MdEditor);
app.component("v-chart", ECharts);

NProgress.configure({ easing: "ease", speed: 500, showSpinner: false, trickleSpeed: 200, minimum: 0.3 });
router.beforeEach((to, from, next) => {
  NProgress.start();
  next(to.path === "/login" || store.state.userId ? undefined : { path: "/login" });
});
router.afterEach(() => NProgress.done());

axios.interceptors.response.use(
  response => {
    if (response.data.code === 40001) {
      app.config.globalProperties.$message.error(response.data.message);
      router.push({ path: "/login" });
    } else if (response.data.code === 50000) {
      app.config.globalProperties.$message.error(response.data.message);
    }
    return response;
  },
  error => Promise.reject(error)
);

app.mount("#app");
