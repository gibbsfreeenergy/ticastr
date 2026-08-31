package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.infrastructure.cache.CacheStore;
import com.wzh.blog.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis cache adapter with miss-on-failure semantics. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisCacheStore implements CacheStore {

    private final RedisService redisService;

    public RedisCacheStore(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public Object get(String key) {
        try {
            return redisService.get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return;
            }
            redisService.set(key, value, ttl.toSeconds());
        } catch (RuntimeException ignored) {
            // Cache failures are deliberately non-fatal.
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisService.del(key);
        } catch (RuntimeException ignored) {
            // Cache failures are deliberately non-fatal.
        }
    }
}
