package com.wzh.blog.service;

import java.time.Duration;

/** Typed boundary for short-lived distributed locks. */
public interface LockStore {

    String tryLock(String key, Duration ttl);

    void release(String key, String token);
}
