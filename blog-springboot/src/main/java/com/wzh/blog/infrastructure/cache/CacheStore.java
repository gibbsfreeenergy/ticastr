package com.wzh.blog.infrastructure.cache;

import java.time.Duration;

/** Best-effort cache port. Cache failures must never become fact failures. */
public interface CacheStore {

    Object get(String key);

    void put(String key, Object value, Duration ttl);

    void evict(String key);
}
