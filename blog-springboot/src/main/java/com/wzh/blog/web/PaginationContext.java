package com.wzh.blog.web;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PaginationContext {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_SIZE = 10L;

    private long current = DEFAULT_CURRENT;
    private long size = DEFAULT_SIZE;

    public void set(long current, long size) {
        this.current = current;
        this.size = size;
    }

    public long getCurrent() {
        return current;
    }

    public long getSize() {
        return size;
    }

    public long getOffset() {
        return (current - 1) * size;
    }
}
