package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.service.ChatPresenceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Redis implementation of the chat presence port. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisChatPresenceStore implements ChatPresenceStore {

    public static final String KEY = "ticastr:chat:online-sessions:v1";
    private static final long TTL_MILLIS = 90_000;

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public RedisChatPresenceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(String sessionId) {
        touch(sessionId);
    }

    @Override
    public void touch(String sessionId) {
        cleanup();
        redisTemplate.opsForZSet().add(KEY, instanceId + ":" + sessionId, System.currentTimeMillis());
    }

    @Override
    public void unregister(String sessionId) {
        redisTemplate.opsForZSet().remove(KEY, instanceId + ":" + sessionId);
        cleanup();
    }

    @Override
    public long count() {
        cleanup();
        Long count = redisTemplate.opsForZSet().zCard(KEY);
        return count == null ? 0L : count;
    }

    private void cleanup() {
        redisTemplate.opsForZSet().removeRangeByScore(KEY, 0, System.currentTimeMillis() - TTL_MILLIS);
    }
}
