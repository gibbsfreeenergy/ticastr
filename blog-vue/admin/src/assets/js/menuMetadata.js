const MENU_METADATA = Object.freeze({
  home: { section: "workspace", iconKey: "home" },
  article: { section: "content", iconKey: "pen" },
  articleList: { section: "content", iconKey: "file" },
  category: { section: "content", iconKey: "folder" },
  tag: { section: "content", iconKey: "tag" },
  album: { section: "content", iconKey: "image" },
  photo: { section: "content", iconKey: "image" },
  talk: { section: "content", iconKey: "bubble" },
  talkList: { section: "content", iconKey: "list" },
  comment: { section: "community", iconKey: "comment" },
  message: { section: "community", iconKey: "message" },
  user: { section: "community", iconKey: "users" },
  online: { section: "community", iconKey: "user" },
  role: { section: "settings", iconKey: "shield" },
  resource: { section: "settings", iconKey: "code" },
  menu: { section: "settings", iconKey: "list" },
  website: { section: "settings", iconKey: "globe" },
  page: { section: "settings", iconKey: "page" },
  friendLink: { section: "settings", iconKey: "link" },
  about: { section: "settings", iconKey: "info" },
  operation: { section: "settings", iconKey: "history" },
  setting: { section: "settings", iconKey: "settings" }
});

const SECTION_LABELS = Object.freeze({
  workspace: "工作台",
  content: "内容管理",
  community: "互动社区",
  settings: "系统设置"
});

export function menuMetadata(routeKey) {
  return MENU_METADATA[routeKey] || { section: "settings", iconKey: "grid" };
}

export function sectionLabel(section) {
  return SECTION_LABELS[section] || SECTION_LABELS.settings;
}

export function decorateMenuEntry(entry, routeKey) {
  const metadata = menuMetadata(routeKey);
  return {
    ...entry,
    code: entry.code || routeKey,
    routeKey: entry.routeKey || routeKey,
    section: entry.section || metadata.section,
    iconKey: entry.iconKey || metadata.iconKey
  };
}
