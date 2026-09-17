package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 首页文章卡片。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleHomeDTO {

    private Integer id;
    private String articleCover;
    private String articleTitle;
    private LocalDateTime createTime;
    private Integer isTop;
    private Integer type;
}
