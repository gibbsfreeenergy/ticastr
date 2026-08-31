package com.wzh.blog.infrastructure.local;

import com.wzh.blog.service.LockStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ownership-token lock for scheduled work in one API instance. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalLockStore implements LockStore {

    private final Map<String, Lease> leases = new HashMap<>();

    @Override
    public synchronized String tryLock(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Lock key must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Lock TTL must be positive");
        }
        long now = System.currentTimeMillis();
        Lease current = leases.get(key);
        if (current != null && current.expiresAt() > now) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        leases.put(key, new Lease(token, now + ttl.toMillis()));
        return token;
    }

    @Override
    public synchronized void release(String key, String token) {
        Lease current = leases.get(key);
        if (current != null && current.token().equals(token)) {
            leases.remove(key);
        }
    }

    private record Lease(String token, long expiresAt) {
    }
}
