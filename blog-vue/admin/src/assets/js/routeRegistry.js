/**
 * Explicit backend route-key -> Vue component registry.
 * The backend must send a routeKey (legacy component paths are accepted only
 * during migration).  Unknown keys fail while the menu is loading instead of
 * producing a broken route after a user clicks it.
 */
export const routeRegistry = Object.freeze({
  about: () => import("../../views/about/About.vue"),
  album: () => import("../../views/album/Album.vue"),
  albumDelete: () => import("../../views/album/Delete.vue"),
  photo: () => import("../../views/album/Photo.vue"),
  article: () => import("../../views/article/Article.vue"),
  articleList: () => import("../../views/article/ArticleList.vue"),
  category: () => import("../../views/category/Category.vue"),
  comment: () => import("../../views/comment/Comment.vue"),
  friendLink: () => import("../../views/friendLink/FriendLink.vue"),
  home: () => import("../../views/home/Home.vue"),
  operation: () => import("../../views/log/Operation.vue"),
  menu: () => import("../../views/menu/Menu.vue"),
  message: () => import("../../views/message/Message.vue"),
  page: () => import("../../views/page/Page.vue"),
  resource: () => import("../../views/resource/Resource.vue"),
  role: () => import("../../views/role/Role.vue"),
  setting: () => import("../../views/setting/Setting.vue"),
  tag: () => import("../../views/tag/Tag.vue"),
  talk: () => import("../../views/talk/Talk.vue"),
  talkList: () => import("../../views/talk/TalkList.vue"),
  online: () => import("../../views/user/Online.vue"),
  user: () => import("../../views/user/User.vue"),
  website: () => import("../../views/website/Website.vue")
});

const legacyComponentKeys = Object.freeze({
  "/about/About.vue": "about",
  "/album/Album.vue": "album",
  "/album/Delete.vue": "albumDelete",
  "/album/Photo.vue": "photo",
  "/article/Article.vue": "article",
  "/article/ArticleList.vue": "articleList",
  "/category/Category.vue": "category",
  "/comment/Comment.vue": "comment",
  "/friendLink/FriendLink.vue": "friendLink",
  "/home/Home.vue": "home",
  "/log/Operation.vue": "operation",
  "/menu/Menu.vue": "menu",
  "/message/Message.vue": "message",
  "/page/Page.vue": "page",
  "/resource/Resource.vue": "resource",
  "/role/Role.vue": "role",
  "/setting/Setting.vue": "setting",
  "/tag/Tag.vue": "tag",
  "/talk/Talk.vue": "talk",
  "/talk/TalkList.vue": "talkList",
  "/user/Online.vue": "online",
  "/user/User.vue": "user",
  "/website/Website.vue": "website"
});

export function resolveRouteKey(routeKey, legacyComponent) {
  const key = routeKey || legacyComponentKeys[legacyComponent];
  return key && routeRegistry[key] ? key : null;
}

export function loadView(routeKey, legacyComponent) {
  const key = resolveRouteKey(routeKey, legacyComponent);
  return key ? routeRegistry[key] : undefined;
}
