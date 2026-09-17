package com.wzh.blog.service.impl;

import com.wzh.blog.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Redis cache adapter used only when the optional Redis cache is enabled. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisServiceImpl implements RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, Object value, long timeSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeSeconds));
    }

    @Override
    public Boolean del(String key) {
        return redisTemplate.delete(key);
    }
}
