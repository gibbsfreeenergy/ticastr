package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 资源
 *
 * @author yezhiqiu
 * @date 2021/08/03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "资源")
public class ResourceVO {

    /**
     * 资源id
     */
    @Schema(description = "资源id")
    private Integer id;

    /**
     * 资源名
     */
    @NotBlank(message = "资源名不能为空")
    @Schema(description = "资源名")
    private String resourceName;

    /**
     * 路径
     */
    @Schema(description = "资源路径")
    private String url;

    /**
     * 请求方式
     */
    @Schema(description = "资源路径")
    private String requestMethod;

    /**
     * 父资源id
     */
    @Schema(description = "父资源id")
    private Integer parentId;

    /**
     * 是否匿名访问
     */
    @Schema(description = "是否匿名访问")
    private Integer isAnonymous;

}
