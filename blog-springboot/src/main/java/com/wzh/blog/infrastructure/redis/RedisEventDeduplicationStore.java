package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.service.EventDeduplicationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis idempotency keys are namespaced and expire after the replay window. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisEventDeduplicationStore implements EventDeduplicationStore {

    private static final String PREFIX = "ticastr:event:processed:v1:";
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public RedisEventDeduplicationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean claim(String eventId, Duration retention) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        Boolean created = redisTemplate.opsForValue().setIfAbsent(PREFIX + eventId, "processing", PROCESSING_LEASE);
        return Boolean.TRUE.equals(created);
    }

    @Override
    public boolean isCompleted(String eventId) {
        return "processed".equals(redisTemplate.opsForValue().get(PREFIX + eventId));
    }

    @Override
    public void complete(String eventId, Duration retention) {
        redisTemplate.opsForValue().set(PREFIX + eventId, "processed", retention);
    }

    @Override
    public void release(String eventId) {
        redisTemplate.delete(PREFIX + eventId);
    }
}
