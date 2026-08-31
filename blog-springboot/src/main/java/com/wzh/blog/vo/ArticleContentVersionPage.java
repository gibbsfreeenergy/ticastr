package com.wzh.blog.vo;

import java.util.List;

/** Bounded cursor page for admin content history. */
public record ArticleContentVersionPage(
        List<ArticleContentVersionVO> items,
        String nextCursor,
        boolean hasNext) {

    public ArticleContentVersionPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
