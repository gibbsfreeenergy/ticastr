package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 后台文章列表项。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleBackDTO {

    private Integer id;
    private String articleCover;
    private String articleTitle;
    private LocalDateTime createTime;
    private Integer type;
    private Integer isTop;
    private Integer isDelete;
    private Integer status;
}
