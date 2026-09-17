package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LoginTypeEnum {
    EMAIL(1, "邮箱登录");

    private final Integer type;
    private final String desc;
}
