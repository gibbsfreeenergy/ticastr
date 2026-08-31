package com.wzh.blog.web;

/** Explicit application-layer pagination value object. */
public record PageQuery(long current, long size) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_SIZE = 10L;
    public static final long MAX_SIZE = 100L;

    public PageQuery {
        if (current < 1) {
            throw new IllegalArgumentException("current must be greater than zero");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(Long current, Long size) {
        return new PageQuery(current == null ? DEFAULT_CURRENT : current,
                size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        try {
            return Math.multiplyExact(current - 1, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("current is too large", exception);
        }
    }
}
