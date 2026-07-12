package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 评论
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "评论")
public class CommentVO {

    /**
     * 回复用户id
     */
    @Schema(description = "回复用户id")
    private Integer replyUserId;

    /**
     * 评论主题id
     */
    @Schema(description = "主题id")
    private Integer topicId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 10000, message = "评论内容不能超过10000个字符")
    @Schema(description = "评论内容")
    private String commentContent;

    /**
     * 父评论id
     */
    @Schema(description = "评论父id")
    private Integer parentId;

    /**
     * 类型
     */
    @NotNull(message = "评论类型不能为空")
    @Schema(description = "评论类型")
    private Integer type;

}
