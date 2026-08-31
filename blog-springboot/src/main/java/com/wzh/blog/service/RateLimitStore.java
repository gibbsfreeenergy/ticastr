package com.wzh.blog.service;

/** Typed boundary for request throttling; key format and TTL stay out of web adapters. */
public interface RateLimitStore {

    /**
     * Increments a bucket and applies its expiry on the first write.
     *
     * @return the counter after the increment
     */
    long increment(String bucket, long expirySeconds);
}
