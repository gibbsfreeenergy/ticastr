const MENU_METADATA = Object.freeze({
  home: { section: "workspace", iconKey: "home" },
  articleGroup: { section: "content", iconKey: "pen" },
  article: { section: "content", iconKey: "pen" },
  articleList: { section: "content", iconKey: "file" },
  about: { section: "content", iconKey: "info" },
  page: { section: "settings", iconKey: "page" },
  website: { section: "settings", iconKey: "globe" },
  setting: { section: "settings", iconKey: "settings" },
  storage: { section: "settings", iconKey: "folder" },
  traffic: { section: "operations", iconKey: "activity" }
});

const SECTION_LABELS = Object.freeze({
  workspace: "工作台",
  content: "内容管理",
  settings: "系统设置",
  operations: "监控与运维"
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
