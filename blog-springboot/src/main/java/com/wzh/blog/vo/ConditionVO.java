package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 查询条件
 *
 * @author yezhiqiu
 * @date 2021/07/29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "查询条件")
public class ConditionVO {

    /**
     * 页码
     */
    @Schema(description = "页码")
    private Long current;

    /**
     * 条数
     */
    @Schema(description = "条数")
    private Long size;

    /**
     * 搜索内容
     */
    @Schema(description = "搜索内容")
    private String keywords;

    /**
     * 分类id
     */
    @Schema(description = "分类id")
    private Integer categoryId;

    /**
     * 标签id
     */
    @Schema(description = "标签id")
    private Integer tagId;

    /**
     * 相册id
     */
    @Schema(description = "相册id")
    private Integer albumId;

    /**
     * 登录类型
     */
    @Schema(description = "登录类型")
    private Integer loginType;

    /**
     * 类型
     */
    @Schema(description = "类型")
    private Integer type;

    /**
     * 状态
     */
    @Schema(description = "状态")
    private Integer status;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 是否删除
     */
    @Schema(description = "是否删除")
    private Integer isDelete;

    /**
     * 是否审核
     */
    @Schema(description = "是否审核")
    private Integer isReview;

}
