package com.wzh.blog.search;

/** Public search projection returned by the local index. */
public record ArticleSearchResult(Integer articleId, String title, String snippet, float score) {
}
