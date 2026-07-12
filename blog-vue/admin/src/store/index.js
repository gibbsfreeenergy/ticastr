import { createStore } from "vuex";
import createPersistedState from "vuex-persistedstate";

export default createStore({
  state: {
    collapse: false,
    tabList: [{ name: "首页", path: "/" }],
    userId: null,
    roleList: null,
    avatar: null,
    nickname: null,
    intro: null,
    webSite: null,
    userMenuList: []
  },
  mutations: {
    saveTab(state, tab) { if (state.tabList.findIndex(item => item.path === tab.path) === -1) state.tabList.push({ name: tab.name, path: tab.path }); },
    removeTab(state, tab) { state.tabList.splice(state.tabList.findIndex(item => item.name === tab.name), 1); },
    resetTab(state) { state.tabList = [{ name: "首页", path: "/" }]; },
    trigger(state) { state.collapse = !state.collapse; },
    login(state, user) { Object.assign(state, { userId: user.userInfoId, roleList: user.roleList, avatar: user.avatar, nickname: user.nickname, intro: user.intro, webSite: user.webSite }); },
    saveUserMenuList(state, userMenuList) { state.userMenuList = userMenuList; },
    logout(state) { Object.assign(state, { userId: null, roleList: null, avatar: null, nickname: null, intro: null, webSite: null, userMenuList: [] }); },
    updateAvatar(state, avatar) { state.avatar = avatar; },
    updateUserInfo(state, user) { Object.assign(state, { nickname: user.nickname, intro: user.intro, webSite: user.webSite }); }
  },
  plugins: [createPersistedState({ storage: window.sessionStorage })]
});
