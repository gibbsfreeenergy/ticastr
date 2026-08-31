import axios from "axios";
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

export function installHttp(app, store) {
  onBusinessResponse = ({ code, message }) => {
      if (code === 40001) {
        app.config.globalProperties.$toast({ type: "error", message });
        store.commit("logout");
      } else if (code === 40300) {
        app.config.globalProperties.$toast({ type: "error", message });
      } else if (code === 50000) {
        app.config.globalProperties.$toast({ type: "error", message: "系统异常，请稍后重试" });
      }
  };
  onHttpError = ({ error, message }) => {
    if (!error.config?.suppressErrorToast) {
      app.config.globalProperties.$toast({ type: "error", message: message || getErrorMessage(error) });
    }
  };
  app.config.globalProperties.$api = api;
}

export default http;
