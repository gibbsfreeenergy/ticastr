package com.wzh.blog.web;

/** Bounded keyset pagination input for public feeds. */
public record CursorPageQuery(String cursor, int size) {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    public CursorPageQuery {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (cursor != null && cursor.length() > 1024) {
            throw new IllegalArgumentException("cursor is too long");
        }
    }

    public static CursorPageQuery of(String cursor, Integer size) {
        return new CursorPageQuery(cursor, size == null ? DEFAULT_SIZE : size);
    }
}
