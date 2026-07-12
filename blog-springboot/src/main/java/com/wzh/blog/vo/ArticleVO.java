package com.wzh.blog.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;


/**
 * 文章
 *
 * @author yezhiqiu
 * @date 2021/08/03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "文章")
public class ArticleVO {

    /**
     * 文章id
     */
    @Schema(description = "文章id")
    private Integer id;

    /**
     * 标题
     */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 255, message = "文章标题不能超过255个字符")
    @Schema(description = "文章标题")
    private String articleTitle;

    /**
     * 内容
     */
    @NotBlank(message = "文章内容不能为空")
    @Size(max = 100000, message = "文章内容不能超过100000个字符")
    @Schema(description = "文章内容")
    private String articleContent;

    /**
     * 文章封面
     */
    @Schema(description = "文章缩略图")
    private String articleCover;

    /**
     * 文章分类
     */
    @Schema(description = "文章分类")
    private String categoryName;

    /**
     * 文章标签
     */
    @Schema(description = "文章标签")
    private List<String> tagNameList;

    /**
     * 文章类型
     */
    @Schema(description = "文章类型")
    private Integer type;

    /**
     * 原文链接
     */
    @Schema(description = "原文链接")
    private String originalUrl;

    /**
     * 是否置顶
     */
    @Schema(description = "是否置顶")
    private Integer isTop;

    /**
     * 文章状态 1.公开 2.私密 3.评论可见
     */
    @Schema(description = "文章状态")
    private Integer status;

}
