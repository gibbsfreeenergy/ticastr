package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.service.RateLimitStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Redis adapter for the rate-limit port. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisRateLimitStore implements RateLimitStore {

    private static final String NAMESPACE = "ticastr:rate-limit:v1:";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long increment(String bucket, long expirySeconds) throws DataAccessException {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Rate-limit bucket must not be blank");
        }
        if (expirySeconds < 1) {
            throw new IllegalArgumentException("Rate-limit expiry must be positive");
        }
        String key = NAMESPACE + bucket;
        Long count = redisTemplate.execute(INCREMENT_WITH_EXPIRY, java.util.List.of(key), String.valueOf(expirySeconds));
        return count == null ? 0L : count;
    }
}
