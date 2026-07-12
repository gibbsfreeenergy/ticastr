import Layout from "@/layout/index.vue";
import router from "../../router";
import store from "../../store";
import axios from "axios";
import { ElMessage } from "element-plus";

const views = import.meta.glob("/src/views/**/*.vue");

export function generaMenu() {
  axios.get("/api/admin/user/menus").then(({ data }) => {
    if (!data.flag) {
      ElMessage.error(data.message);
      router.push({ path: "/login" });
      return;
    }
    const userMenuList = data.data;
    userMenuList.forEach(item => {
      if (item.icon != null) item.icon = "iconfont " + item.icon;
      if (item.component === "Layout") item.component = Layout;
      item.children?.forEach(route => {
        route.icon = "iconfont " + route.icon;
        route.component = loadView(route.component);
      });
      router.addRoute(item);
    });
    store.commit("saveUserMenuList", userMenuList);
  });
}

export const loadView = view => views[`/src/views${view}`];
