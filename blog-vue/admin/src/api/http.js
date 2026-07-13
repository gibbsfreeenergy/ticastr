import axios from "axios";
import { ElMessage } from "element-plus";
import "element-plus/es/components/message/style/css";
import router from "../router";
import store from "../store";

const http = axios.create({
  timeout: 15000,
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  headers: { "X-Requested-With": "XMLHttpRequest" }
});

http.interceptors.response.use(
  response => {
    if (response.data.code === 40001) {
      ElMessage.error(response.data.message);
      store.commit("logout");
      router.push({ path: "/login" });
    } else if (response.data.code === 50000) {
      ElMessage.error(response.data.message);
    }
    return response;
  },
  error => {
    const status = error.response?.status;
    const serverMessage = typeof error.response?.data?.message === "string"
      ? error.response.data.message
      : "";
    const message = serverMessage || (status === 401
      ? "登录已过期，请重新登录"
      : status === 403
        ? "没有执行此操作的权限"
        : status === 404
          ? "请求的内容不存在"
          : status === 409
            ? "数据已发生变化，请刷新后重试"
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

export function installHttp(app) {
  app.config.globalProperties.$http = http;
}

export default http;
