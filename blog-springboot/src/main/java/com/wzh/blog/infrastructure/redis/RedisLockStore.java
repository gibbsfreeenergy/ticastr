package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.service.LockStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Redis adapter for ownership-token locks. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisLockStore implements LockStore {

    private static final String NAMESPACE = "ticastr:lock:v1:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String tryLock(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Lock key must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Lock TTL must be positive");
        }
        String namespacedKey = NAMESPACE + key;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(namespacedKey, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    @Override
    public void release(String key, String token) {
        if (key != null && !key.isBlank() && token != null) {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(NAMESPACE + key), token);
        }
    }
}
