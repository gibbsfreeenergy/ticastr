package com.wzh.blog.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatusCodeEnum {
    SUCCESS(20000, "操作成功"),
    NO_LOGIN(40001, "用户未登录"),
    AUTHORIZED(40300, "没有操作权限"),
    SYSTEM_ERROR(50000, "系统异常"),
    FAIL(51000, "操作失败"),
    VALID_ERROR(52000, "参数格式不正确"),
    NOT_FOUND(54004, "资源不存在"),
    CONFLICT(54009, "数据冲突"),
    USERNAME_EXIST(52001, "用户名已存在"),
    USERNAME_NOT_EXIST(52002, "用户名不存在");

    private final Integer code;
    private final String desc;
}
