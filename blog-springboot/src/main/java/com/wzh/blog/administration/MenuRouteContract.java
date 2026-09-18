package com.wzh.blog.administration;

import com.wzh.blog.entity.Menu;

import java.util.Map;

/**
 * Stable identifiers for the small administrator navigation surface.
 */
public final class MenuRouteContract {

    private static final Map<String, String> PATH_KEYS = Map.of(
            "/", "home",
            "/articles", "articleList",
            "/articles/:articleId", "article",
            "/articles/*", "article",
            "/about", "about",
            "/pages", "page",
            "/website", "website",
            "/setting", "setting",
            "/storage", "storage",
            "/traffic", "traffic");

    private static final Map<String, String> COMPONENT_KEYS = Map.of(
            "/home/Home.vue", "home",
            "/article/Article.vue", "article",
            "/article/ArticleList.vue", "articleList",
            "/about/About.vue", "about",
            "/page/Page.vue", "page",
            "/website/Website.vue", "website",
            "/setting/Setting.vue", "setting",
            "/storage/Storage.vue", "storage",
            "/traffic/Traffic.vue", "traffic");

    private MenuRouteContract() {
    }

    public static void normalize(Menu menu) {
        String key = firstNonBlank(
                menu.getRouteKey(),
                lookup(PATH_KEYS, menu.getPath()),
                lookup(COMPONENT_KEYS, menu.getComponent()),
                "menu-" + menu.getId());
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

    public static String sectionFor(String routeKey) {
        if ("home".equals(routeKey)) {
            return "workspace";
        }
        if ("article".equals(routeKey) || "articleList".equals(routeKey)
                || "about".equals(routeKey)) {
            return "content";
        }
        if ("traffic".equals(routeKey)) {
            return "operations";
        }
        return "settings";
    }

    public static String iconFor(String routeKey) {
        return switch (routeKey) {
            case "home" -> "home";
            case "article", "articleList" -> "pen";
            case "about" -> "info";
            case "page" -> "page";
            case "website" -> "globe";
            case "storage" -> "folder";
            case "setting" -> "settings";
            case "traffic" -> "activity";
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
