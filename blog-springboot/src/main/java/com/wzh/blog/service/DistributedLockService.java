package com.wzh.blog.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

/** Small ownership-token Redis lock for short scheduled jobs. */
@Service
public class DistributedLockService {

    private final LockStore lockStore;

    public DistributedLockService(LockStore lockStore) {
        this.lockStore = lockStore;
    }

    public String tryLock(String key, Duration ttl) {
        return lockStore.tryLock(key, ttl);
    }

    public void release(String key, String token) {
        if (token != null) {
            lockStore.release(key, token);
        }
    }
}
