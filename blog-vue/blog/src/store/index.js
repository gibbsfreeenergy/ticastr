import { createStore } from "vuex";

export default createStore({
  state: {
    searchFlag: false,
    drawer: false,
    blogInfo: {
      websiteConfig: {
        websiteName: "个人博客",
        websiteAuthor: "博客作者",
        websiteIntro: "记录生活，分享技术",
        websiteNotice: "",
        websiteAvatar: "",
        websiteCreateTime: ""
      },
      pageList: [],
      articleCount: 0
    }
  },
  mutations: {
    checkBlogInfo(state, blogInfo) {
      state.blogInfo = {
        ...state.blogInfo,
        ...blogInfo,
        websiteConfig: {
          ...state.blogInfo.websiteConfig,
          ...(blogInfo.websiteConfig || {})
        }
      };
    }
  }
});
