package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Keeps the database-backed authorization map coherent across API replicas. */
@Service
public class AuthorizationCacheService implements MessageListener {

    public static final String CHANNEL = "ticastr:authorization:invalidate";

    private final FilterInvocationSecurityMetadataSourceImpl metadataSource;
    private final StringRedisTemplate redisTemplate;

    public AuthorizationCacheService(FilterInvocationSecurityMetadataSourceImpl metadataSource,
                                     StringRedisTemplate redisTemplate) {
        this.metadataSource = metadataSource;
        this.redisTemplate = redisTemplate;
    }

    public void invalidate() {
        metadataSource.clearDataSource();
        redisTemplate.convertAndSend(CHANNEL, "invalidate");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        metadataSource.clearDataSource();
    }
}
