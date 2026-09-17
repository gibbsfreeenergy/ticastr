package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RoleEnum {
    ADMIN("管理员", "admin");

    private final String name;
    private final String label;
}
