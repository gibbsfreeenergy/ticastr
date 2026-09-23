import Layout from "@/layout/index.vue";
import router from "../../router";
import store from "../../store";
import { api } from "../../api/http";
import { ElMessage } from "element-plus";
import { decorateMenuEntry } from "./menuMetadata";
import { loadView, resolveRouteKey } from "./routeRegistry";
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
  const data = await api.admin.menus();
  if (!data.flag) {
    ElMessage.error(data.message);
    throw new Error(data.message);
  }
  const userMenuList = data.data;
  const normalizedMenuList = userMenuList.map(item => {
    const routeKey = item.routeKey || item.code || resolveRouteKey(null, item.component);
    const normalizedItem = decorateMenuEntry(item, routeKey);
    normalizedItem.children = (item.children || []).map(route => {
      const childRouteKey = route.routeKey || route.code || resolveRouteKey(null, route.component);
      const component = loadView(childRouteKey, route.component);
      if (!component) {
        throw new Error(`Unknown menu route key: ${childRouteKey || route.path}`);
      }
      const normalizedRoute = decorateMenuEntry(route, childRouteKey);
      normalizedRoute.component = component;
      return normalizedRoute;
    });
    const hasChildren = normalizedItem.children.length > 0;
    const standaloneComponent = loadView(routeKey, normalizedItem.component);
    normalizedItem.component = Layout;
    const sharesChildName = normalizedItem.children.some(child => child.name === normalizedItem.name);
    const route = hasChildren
      ? (sharesChildName
          ? {
              ...normalizedItem,
              name: `${normalizedItem.code || normalizedItem.path}:layout`,
              meta: { ...(normalizedItem.meta || {}), layoutOnly: true }
            }
          : normalizedItem)
      : standaloneComponent
        ? {
            ...normalizedItem,
            children: [{
              path: "",
              name: `${normalizedItem.code || normalizedItem.path}:view`,
              component: standaloneComponent,
              meta: { hidden: true, layoutView: true, title: normalizedItem.name }
            }]
          }
        : normalizedItem;
    router.addRoute(route);
    return normalizedItem;
  });
  store.commit("saveUserMenuList", normalizedMenuList);
  menuReady = true;
  return normalizedMenuList;
}

export const isMenuReady = () => menuReady;

export function resetMenuLoader() {
  menuPromise = undefined;
  menuReady = false;
}
