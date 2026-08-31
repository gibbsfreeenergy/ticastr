package com.wzh.blog.security;

import com.wzh.blog.service.RateLimitStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded fallback rate limiter used when the optional Redis path is absent. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class BoundedInMemoryRateLimitStore implements RateLimitStore {

    private static final int MAX_BUCKETS = 10_000;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public long increment(String bucket, long expirySeconds) {
        if (bucket == null || bucket.isBlank() || expirySeconds < 1) {
            throw new IllegalArgumentException("Invalid rate-limit bucket");
        }
        long now = System.currentTimeMillis();
        Bucket result = buckets.compute(bucket, (key, current) -> {
            if (current == null || current.expiresAt <= now) {
                return new Bucket(1, now + expirySeconds * 1000);
            }
            return new Bucket(current.count + 1, current.expiresAt);
        });
        if (buckets.size() > MAX_BUCKETS) {
            evictExpired(now);
        }
        return result.count;
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) {
                iterator.remove();
            }
        }
        if (buckets.size() > MAX_BUCKETS) {
            // A bounded rejection is safer than unbounded memory growth. The
            // current bucket remains usable even if older entries are retained.
            buckets.keySet().stream().limit(buckets.size() - MAX_BUCKETS).forEach(buckets::remove);
        }
    }

    private record Bucket(long count, long expiresAt) {
    }
}
