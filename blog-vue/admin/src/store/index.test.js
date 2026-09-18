import { beforeEach, describe, expect, it, vi } from "vitest";

const user = {
  userInfoId: 7,
  roleList: ["admin"],
  avatar: "/avatar.png",
  nickname: "管理员",
  intro: "",
  webSite: ""
};

describe("admin session state", () => {
  beforeEach(() => {
    vi.resetModules();
    window.sessionStorage.clear();
  });

  it("restores the logged-in user after a page reload", async () => {
    const { default: initialStore } = await import("./index");
    initialStore.commit("login", user);

    vi.resetModules();
    const { default: reloadedStore } = await import("./index");

    expect(reloadedStore.state.userId).toBe(7);
    expect(reloadedStore.state.nickname).toBe("管理员");
    expect(reloadedStore.state.roleList).toEqual(["admin"]);
  });

  it("clears the persisted user when logging out", async () => {
    const { default: store } = await import("./index");
    store.commit("login", user);
    store.commit("logout");

    expect(window.sessionStorage.getItem("ticastr.admin.session")).toBeNull();
  });
});
