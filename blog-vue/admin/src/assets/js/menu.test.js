import { beforeEach, describe, expect, it, vi } from "vitest";
import router, { resetRouter } from "../../router";
import store from "../../store";

vi.mock("../../api/http", () => ({
  api: { admin: { menus: vi.fn() } }
}));

import { api } from "../../api/http";
import { generaMenu, isMenuReady, resetMenuLoader } from "./menu";

describe("dynamic menu loading", () => {
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
