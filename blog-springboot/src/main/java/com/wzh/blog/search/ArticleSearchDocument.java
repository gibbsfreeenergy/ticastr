package com.wzh.blog.search;

/** 文章搜索投影，只索引标题和正文。 */
public record ArticleSearchDocument(
        Integer articleId,
        String title,
        String body) {

    public ArticleSearchDocument {
        if (articleId == null || articleId < 1) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        title = title == null ? "" : title;
        body = body == null ? "" : body;
    }
}
