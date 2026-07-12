package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 角色
 *
 * @author yezhiqiu
 * @date 2021/08/03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "角色")
public class RoleVO {

    /**
     * id
     */
    @Schema(description = "用户id")
    private Integer id;

    /**
     * 标签名
     */
    @NotBlank(message = "角色名不能为空")
    @Schema(description = "角色名")
    private String roleName;

    /**
     * 标签名
     */
    @NotBlank(message = "权限标签不能为空")
    @Schema(description = "标签名")
    private String roleLabel;

    /**
     * 资源列表
     */
    @Schema(description = "资源列表")
    private List<Integer> resourceIdList;

    /**
     * 菜单列表
     */
    @Schema(description = "菜单列表")
    private List<Integer> menuIdList;

}
