import { beforeEach, describe, expect, it, vi } from "vitest";
import router, { resetRouter } from "../../router";
import store from "../../store";
import Layout from "@/layout/index.vue";

vi.mock("../../api/http", () => ({
  api: { admin: { menus: vi.fn() } }
}));

import { api } from "../../api/http";
import { generaMenu, isMenuReady, resetMenuLoader } from "./menu";

describe("dynamic menu loading", () => {
  it("registers the standalone storage menu supplied by the API", async () => {
    api.admin.menus.mockResolvedValue({
      flag: true,
      data: [{
        path: "/storage", name: "存储源配置", component: "Layout", routeKey: "storage",
        children: [{ path: "", name: "存储源配置", routeKey: "storage", component: "/storage/Storage.vue" }]
      }]
    });
    await generaMenu();
    expect(router.resolve("/storage").matched.at(-1).components.default).toBeDefined();
    expect(store.state.userMenuList[0].section).toBe("settings");
  });

  it("mounts standalone Layout menu entries on their registered views", async () => {
    api.admin.menus.mockResolvedValue({
      flag: true,
      data: [
        { path: "/", name: "首页", code: "home", routeKey: "home", component: "Layout", children: [] },
        { path: "/traffic", name: "代理监控", code: "traffic", routeKey: "traffic", component: "Layout", children: [] }
      ]
    });

    await generaMenu();

    expect(router.resolve("/").matched.at(-1).components.default).toBeDefined();
    expect(router.resolve("/traffic").matched.at(-1).components.default).toBeDefined();
    expect(router.resolve("/").matched.at(-1).components.default).not.toBe(Layout);
  });

  beforeEach(() => {
    resetRouter();
    resetMenuLoader();
    store.commit("saveUserMenuList", []);
  });

  it("waits for menu data before marking routes ready", async () => {
    api.admin.menus.mockResolvedValue({
      flag: true,
      data: [{
        path: "/",
        name: "home",
        component: "Layout",
        children: [{ path: "home", name: "dashboard", component: "/home/Home.vue", icon: "home" }]
      }]
    });

    await generaMenu();

    expect(isMenuReady()).toBe(true);
    expect(router.hasRoute("home")).toBe(true);
    expect(router.hasRoute("dashboard")).toBe(true);
  });
});
