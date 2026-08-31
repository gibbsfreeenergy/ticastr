package com.wzh.blog.search;

import java.util.List;

/** Search projection; Markdown remains in object storage and is never persisted here as a fact. */
public record ArticleSearchDocument(
        Integer articleId,
        String title,
        Integer categoryId,
        List<String> tagNames,
        String body) {

    public ArticleSearchDocument {
        if (articleId == null || articleId < 1) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        title = title == null ? "" : title;
        tagNames = tagNames == null ? List.of() : List.copyOf(tagNames);
        body = body == null ? "" : body;
    }
}
