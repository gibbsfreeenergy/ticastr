package com.wzh.blog.web;

import java.util.List;

/** Stable response shape for public keyset-paginated feeds. */
public record CursorPageResult<T>(List<T> items, String nextCursor, boolean hasNext) {

    public CursorPageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
