package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 首页和关于页使用的网站基础配置。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "网站基础配置")
public class WebsiteConfigVO {

    @Schema(description = "网站头像")
    private String websiteAvatar;

    @Schema(description = "网站名称")
    private String websiteName;

    @Schema(description = "网站作者")
    private String websiteAuthor;

    @Schema(description = "网站介绍")
    private String websiteIntro;

    @Schema(description = "网站公告")
    private String websiteNotice;

    @Schema(description = "网站创建时间")
    private String websiteCreateTime;

    @Schema(description = "网站备案号")
    private String websiteRecordNo;

    @Schema(description = "社交链接")
    private List<String> socialUrlList;

    @Schema(description = "QQ")
    private String qq;

    @Schema(description = "GitHub")
    private String github;

    @Schema(description = "Gitee")
    private String gitee;
}
