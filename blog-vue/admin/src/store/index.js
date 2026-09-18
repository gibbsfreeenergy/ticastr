import { createStore } from "vuex";

const ADMIN_SESSION_KEY = "ticastr.admin.session";
const userStateKeys = ["userId", "roleList", "avatar", "nickname", "intro", "webSite"];

function readPersistedUser() {
  if (typeof window === "undefined") return {};
  try {
    const value = JSON.parse(window.sessionStorage.getItem(ADMIN_SESSION_KEY) || "null");
    if (!value || typeof value !== "object" || !value.userId) return {};
    return userStateKeys.reduce((user, key) => {
      user[key] = value[key] ?? null;
      return user;
    }, {});
  } catch (_error) {
    window.sessionStorage.removeItem(ADMIN_SESSION_KEY);
    return {};
  }
}

function persistUser(state) {
  if (typeof window === "undefined") return;
  const user = userStateKeys.reduce((value, key) => {
    value[key] = state[key];
    return value;
  }, {});
  window.sessionStorage.setItem(ADMIN_SESSION_KEY, JSON.stringify(user));
}

function clearPersistedUser() {
  if (typeof window !== "undefined") window.sessionStorage.removeItem(ADMIN_SESSION_KEY);
}

const restoredUser = readPersistedUser();

export default createStore({
  state: {
    collapse: false,
    tabList: [{ name: "首页", path: "/" }],
    userId: restoredUser.userId || null,
    roleList: restoredUser.roleList || null,
    avatar: restoredUser.avatar || null,
    nickname: restoredUser.nickname || null,
    intro: restoredUser.intro || null,
    webSite: restoredUser.webSite || null,
    userMenuList: []
  },
  mutations: {
    saveTab(state, tab) { if (state.tabList.findIndex(item => item.path === tab.path) === -1) state.tabList.push({ name: tab.name, path: tab.path }); },
    removeTab(state, tab) { state.tabList.splice(state.tabList.findIndex(item => item.name === tab.name), 1); },
    resetTab(state) { state.tabList = [{ name: "首页", path: "/" }]; },
    trigger(state) { state.collapse = !state.collapse; },
    login(state, user) {
      Object.assign(state, { userId: user.userInfoId, roleList: user.roleList, avatar: user.avatar, nickname: user.nickname, intro: user.intro, webSite: user.webSite });
      persistUser(state);
    },
    saveUserMenuList(state, userMenuList) { state.userMenuList = userMenuList; },
    logout(state) {
      Object.assign(state, { userId: null, roleList: null, avatar: null, nickname: null, intro: null, webSite: null, userMenuList: [] });
      clearPersistedUser();
    },
    updateAvatar(state, avatar) { state.avatar = avatar; persistUser(state); },
    updateUserInfo(state, user) {
      Object.assign(state, { nickname: user.nickname, intro: user.intro, webSite: user.webSite });
      persistUser(state);
    }
  },
});
