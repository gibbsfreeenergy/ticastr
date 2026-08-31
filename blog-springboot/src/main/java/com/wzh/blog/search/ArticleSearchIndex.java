package com.wzh.blog.search;

import java.util.List;

/** Provider-neutral boundary for the rebuildable local search index. */
public interface ArticleSearchIndex {

    void upsert(ArticleSearchDocument document);

    void delete(Integer articleId);

    List<ArticleSearchResult> search(String query, int offset, int limit);

    void rebuild(List<ArticleSearchDocument> documents);

    long documentCount();
}
