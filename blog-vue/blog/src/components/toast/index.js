import { createApp } from "vue";
import ToastComponent from "./Toast.vue";
import vuetify from "../../plugins/vuetify";

export default {
  install(app) {
    const host = document.createElement("div");
    const toastApp = createApp(ToastComponent);
    toastApp.use(vuetify);
    const instance = toastApp.mount(host);
    document.body.appendChild(host);
    app.config.globalProperties.$toast = (options, duration = 2000) => {
      instance.message = options.message;
      instance.type = options.type;
      instance.show = true;
      window.setTimeout(() => { instance.show = false; }, duration);
    };
  }
};
