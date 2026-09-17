package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleDTO {

    private Integer id;
    private String articleCover;
    private String articleTitle;
    private Integer contentVersion;
    private String contentUrl;
    private Integer type;
    private String originalUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private ArticlePaginationDTO lastArticle;
    private ArticlePaginationDTO nextArticle;
    private List<ArticleRecommendDTO> recommendArticleList;
    private List<ArticleRecommendDTO> newestArticleList;
}
