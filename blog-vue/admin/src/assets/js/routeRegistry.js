/**
 * Components that can be returned by the trimmed administrator menu.
 */
export const routeRegistry = Object.freeze({
  home: () => import("../../views/home/Home.vue"),
  article: () => import("../../views/article/Article.vue"),
  articleList: () => import("../../views/article/ArticleList.vue"),
  about: () => import("../../views/about/About.vue"),
  page: () => import("../../views/page/Page.vue"),
  website: () => import("../../views/website/Website.vue"),
  setting: () => import("../../views/setting/Setting.vue"),
  storage: () => import("../../views/storage/Storage.vue"),
  traffic: () => import("../../views/traffic/Traffic.vue")
});

const legacyComponentKeys = Object.freeze({
  "/home/Home.vue": "home",
  "/article/Article.vue": "article",
  "/article/ArticleList.vue": "articleList",
  "/about/About.vue": "about",
  "/page/Page.vue": "page",
  "/website/Website.vue": "website",
  "/setting/Setting.vue": "setting",
  "/storage/Storage.vue": "storage",
  "/traffic/Traffic.vue": "traffic"
});

export function resolveRouteKey(routeKey, legacyComponent) {
  const key = routeKey || legacyComponentKeys[legacyComponent];
  return key && routeRegistry[key] ? key : null;
}

export function loadView(routeKey, legacyComponent) {
  const key = resolveRouteKey(routeKey, legacyComponent);
  return key ? routeRegistry[key] : undefined;
}
