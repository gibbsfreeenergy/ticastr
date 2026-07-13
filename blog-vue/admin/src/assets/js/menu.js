import Layout from "@/layout/index.vue";
import router from "../../router";
import store from "../../store";
import http from "../../api/http";
import { ElMessage } from "element-plus";

const views = import.meta.glob("/src/views/**/*.vue");
let menuPromise;
let menuReady = false;

export function generaMenu() {
  if (!menuPromise) {
    menuPromise = loadMenus().catch(error => {
      menuPromise = undefined;
      menuReady = false;
      throw error;
    });
  }
  return menuPromise;
}

async function loadMenus() {
  const { data } = await http.get("/api/admin/user/menus");
  if (!data.flag) {
    ElMessage.error(data.message);
    throw new Error(data.message);
  }
  const userMenuList = data.data;
  userMenuList.forEach(item => {
    if (item.icon != null) item.icon = "iconfont " + item.icon;
    if (item.component === "Layout") item.component = Layout;
    item.children?.forEach(route => {
      route.icon = "iconfont " + route.icon;
      route.component = loadView(route.component);
      if (!route.component) {
        throw new Error(`Unknown menu component: ${route.path}`);
      }
    });
    router.addRoute(item);
  });
  store.commit("saveUserMenuList", userMenuList);
  menuReady = true;
  return userMenuList;
}

export const loadView = view => views[`/src/views${view}`];

export const isMenuReady = () => menuReady;

export function resetMenuLoader() {
  menuPromise = undefined;
  menuReady = false;
}
