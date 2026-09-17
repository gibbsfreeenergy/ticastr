package com.wzh.blog.service;

/** Optional Redis-backed cache operations used by the retained blog core. */
public interface RedisService {

    Object get(String key);

    void set(String key, Object value, long timeSeconds);

    Boolean del(String key);
}
