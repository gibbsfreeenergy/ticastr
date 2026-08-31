package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.wzh.blog.service.AuthorizationInvalidationPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Redis adapter for cross-node authorization metadata invalidation. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisAuthorizationInvalidationBus
        implements AuthorizationInvalidationPublisher, MessageListener {

    public static final String CHANNEL = "ticastr:authorization:invalidate:v1";

    private final StringRedisTemplate redisTemplate;
    private final FilterInvocationSecurityMetadataSourceImpl metadataSource;

    public RedisAuthorizationInvalidationBus(StringRedisTemplate redisTemplate,
                                             FilterInvocationSecurityMetadataSourceImpl metadataSource) {
        this.redisTemplate = redisTemplate;
        this.metadataSource = metadataSource;
    }

    @Override
    public void publish() {
        redisTemplate.convertAndSend(CHANNEL, "invalidate");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        metadataSource.clearDataSource();
    }
}
