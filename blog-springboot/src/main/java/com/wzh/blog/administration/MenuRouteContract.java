package com.wzh.blog.administration;

import com.wzh.blog.entity.Menu;

import java.util.Map;

/**
 * Stable menu metadata shared by the administration application boundary.
 *
 * <p>The database still contains the legacy component and icon columns for
 * rolling upgrades. They are read only as a migration fallback; UI behavior
 * is keyed by routeKey/code instead of display text or source file paths.</p>
 */
public final class MenuRouteContract {

    private static final Map<String, String> PATH_KEYS = Map.ofEntries(
            Map.entry("/", "home"),
            Map.entry("/articles", "article"),
            Map.entry("/articles/*", "article"),
            Map.entry("/article-list", "articleList"),
            Map.entry("/categories", "category"),
            Map.entry("/tags", "tag"),
            Map.entry("/albums", "album"),
            Map.entry("/albums/:albumId", "photo"),
            Map.entry("/photos/delete", "albumDelete"),
            Map.entry("/comments", "comment"),
            Map.entry("/messages", "message"),
            Map.entry("/users", "user"),
            Map.entry("/online/users", "online"),
            Map.entry("/roles", "role"),
            Map.entry("/resources", "resource"),
            Map.entry("/menus", "menu"),
            Map.entry("/links", "friendLink"),
            Map.entry("/about", "about"),
            Map.entry("/operation/log", "operation"),
            Map.entry("/pages", "page"),
            Map.entry("/website", "website"),
            Map.entry("/setting", "setting"),
            Map.entry("/talks", "talk"),
            Map.entry("/talks/:talkId", "talk"),
            Map.entry("/talk-list", "talkList"),
            Map.entry("/article-submenu", "articleGroup"),
            Map.entry("/message-submenu", "messageGroup"),
            Map.entry("/system-submenu", "systemGroup"),
            Map.entry("/users-submenu", "userGroup"),
            Map.entry("/permission-submenu", "permissionGroup"),
            Map.entry("/album-submenu", "albumGroup"),
            Map.entry("/talk-submenu", "talkGroup"),
            Map.entry("/log-submenu", "logGroup")
    );

    private static final Map<String, String> COMPONENT_KEYS = Map.ofEntries(
            Map.entry("/home/Home.vue", "home"),
            Map.entry("/article/Article.vue", "article"),
            Map.entry("/article/ArticleList.vue", "articleList"),
            Map.entry("/category/Category.vue", "category"),
            Map.entry("/tag/Tag.vue", "tag"),
            Map.entry("/album/Album.vue", "album"),
            Map.entry("/album/Photo.vue", "photo"),
            Map.entry("/album/Delete.vue", "albumDelete"),
            Map.entry("/comment/Comment.vue", "comment"),
            Map.entry("/message/Message.vue", "message"),
            Map.entry("/user/User.vue", "user"),
            Map.entry("/user/Online.vue", "online"),
            Map.entry("/role/Role.vue", "role"),
            Map.entry("/resource/Resource.vue", "resource"),
            Map.entry("/menu/Menu.vue", "menu"),
            Map.entry("/friendLink/FriendLink.vue", "friendLink"),
            Map.entry("/about/About.vue", "about"),
            Map.entry("/log/Operation.vue", "operation"),
            Map.entry("/page/Page.vue", "page"),
            Map.entry("/website/Website.vue", "website"),
            Map.entry("/setting/Setting.vue", "setting"),
            Map.entry("/talk/Talk.vue", "talk"),
            Map.entry("/talk/TalkList.vue", "talkList")
    );

    private MenuRouteContract() {
    }

    public static void normalize(Menu menu) {
        String key = firstNonBlank(menu.getRouteKey(), lookup(PATH_KEYS, menu.getPath()),
                lookup(COMPONENT_KEYS, menu.getComponent()), "menu-" + menu.getId());
        if (isBlank(menu.getCode())) {
            menu.setCode(key);
        }
        if (isBlank(menu.getRouteKey())) {
            menu.setRouteKey(key);
        }
        if (isBlank(menu.getSection())) {
            menu.setSection(sectionFor(key));
        }
        if (isBlank(menu.getIconKey())) {
            menu.setIconKey(iconFor(key));
        }
    }

    public static String routeKey(Menu menu) {
        normalize(menu);
        return menu.getRouteKey();
    }

    public static String sectionFor(String routeKey) {
        if (routeKey == null) {
            return "settings";
        }
        if (routeKey.equals("home")) {
            return "workspace";
        }
        if (routeKey.matches("article.*|category|tag|album.*|photo|talk.*")) {
            return "content";
        }
        if (routeKey.matches("comment|message|user|online")) {
            return "community";
        }
        return "settings";
    }

    public static String iconFor(String routeKey) {
        if (routeKey == null) {
            return "grid";
        }
        return switch (routeKey) {
            case "home" -> "home";
            case "article", "articleGroup" -> "pen";
            case "articleList", "talkList", "menu" -> "list";
            case "category", "album", "photo", "albumGroup" -> "folder";
            case "tag" -> "tag";
            case "talk", "talkGroup" -> "bubble";
            case "comment" -> "comment";
            case "message" -> "message";
            case "user", "userGroup" -> "users";
            case "online" -> "user";
            case "role", "permissionGroup" -> "shield";
            case "resource" -> "code";
            case "website" -> "globe";
            case "page" -> "page";
            case "friendLink" -> "link";
            case "about" -> "info";
            case "operation", "logGroup" -> "history";
            case "setting", "systemGroup" -> "settings";
            default -> "grid";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "menu-unknown";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String lookup(Map<String, String> values, String key) {
        return key == null ? null : values.get(key);
    }
}
