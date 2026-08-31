import axios from "axios";
import { ElMessage } from "element-plus";
import "element-plus/es/components/message/style/css";
import router from "../router";
import store from "../store";
import { createApi } from "../../../shared/api/createApi";
import { createHttpClient, getErrorMessage } from "../../../shared/http/createHttpClient";

let onBusinessResponse = () => {};
let onHttpError = () => {};

const http = createHttpClient({
  axios,
  onBusinessResponse: event => onBusinessResponse(event),
  onHttpError: event => onHttpError(event)
});

export const api = createApi(http);

export function installHttp(app) {
  onBusinessResponse = ({ code, message }) => {
    if (code === 40001) {
      ElMessage.error(message);
      store.commit("logout");
      router.push({ path: "/login" });
    } else if (code === 50000) {
      ElMessage.error(message);
    }
  };
  onHttpError = ({ error, message, status }) => {
    ElMessage.error(message || getErrorMessage(error));
    if (status === 401) {
      store.commit("logout");
      router.push({ path: "/login" });
    }
  };
  app.config.globalProperties.$api = api;
}

export default http;
