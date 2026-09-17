package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Storage prefixes used by the retained image upload endpoints.
 */
@Getter
@AllArgsConstructor
public enum FilePathEnum {
    AVATAR("avatar/", "头像路径"),
    ARTICLE("articles/", "文章图片路径"),
    CONFIG("config/", "配置图片路径");

    private final String path;
    private final String desc;
}
