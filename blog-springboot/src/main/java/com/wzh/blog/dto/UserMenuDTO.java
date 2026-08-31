package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户菜单
 *
 * @author yezhiqiu
 * @date 2021/08/01
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserMenuDTO {

    /**
     * 菜单名
     */
    private String name;

    private String code;

    /**
     * 路径
     */
    private String path;

    /**
     * 组件
     */
    private String component;

    private String routeKey;

    /**
     * icon
     */
    private String icon;

    private String iconKey;

    private String section;

    /**
     * 是否隐藏
     */
    private Boolean hidden;

    /**
     * 子菜单列表
     */
    private List<UserMenuDTO> children;

}
