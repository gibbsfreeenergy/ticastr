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
 * 留言
 *
 * @author yezhiqiu
 * @date 2021/08/03
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "留言")
public class MessageVO {

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 头像
     */
    @NotBlank(message = "头像不能为空")
    @Schema(description = "头像")
    private String avatar;

    /**
     * 留言内容
     */
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 10000, message = "留言内容不能超过10000个字符")
    @Schema(description = "留言内容")
    private String messageContent;

    /**
     * 弹幕速度
     */
    @NotNull(message = "弹幕速度不能为空")
    @Schema(description = "弹幕速度")
    private Integer time;
}
