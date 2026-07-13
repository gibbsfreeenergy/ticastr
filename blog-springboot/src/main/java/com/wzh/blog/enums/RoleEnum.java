package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 角色枚举
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {
    /**
     * 管理员
     */
    ADMIN("管理员", "admin"),
    /**
     * 普通用户
     */
    USER("用户", "user"),
    /**
     * 测试账号
     */
    TEST("测试", "test");

    /**
     * 描述
     */
    private final String name;

    /**
     * 权限标签
     */
    private final String label;

}
