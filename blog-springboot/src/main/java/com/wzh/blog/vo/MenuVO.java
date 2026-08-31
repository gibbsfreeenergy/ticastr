package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单
 *
 * @author yezhiqiu
 * @date 2021/08/03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "菜单")
public class MenuVO {

    /**
     * id
     */
    @Schema(description = "菜单id")
    private Integer id;

    /**
     * 菜单名
     */
    @NotBlank(message = "菜单名不能为空")
    @Schema(description = "菜单名")
    private String name;

    @Schema(description = "稳定菜单编码")
    private String code;

    /**
     * icon
     */
    @NotBlank(message = "菜单icon不能为空")
    @Schema(description = "菜单icon")
    private String icon;

    @Schema(description = "稳定图标编码")
    private String iconKey;

    @Schema(description = "稳定导航分组编码")
    private String section;

    /**
     * 路径
     */
    @NotBlank(message = "路径不能为空")
    @Schema(description = "路径")
    private String path;

    /**
     * 组件
     */
    @NotBlank(message = "组件不能为空")
    @Schema(description = "组件")
    private String component;

    @Schema(description = "前端路由注册表编码")
    private String routeKey;

    /**
     * 排序
     */
    @NotNull(message = "排序不能为空")
    @Schema(description = "排序")
    private Integer orderNum;

    /**
     * 父id
     */
    @Schema(description = "父id")
    private Integer parentId;

    /**
     * 是否隐藏
     */
    @Schema(description = "是否隐藏")
    private Integer isHidden;

}
