package com.wzh.blog.config;

import com.wzh.blog.service.AuthorizationCacheService;
import com.wzh.blog.infrastructure.redis.RedisAuthorizationInvalidationBus;
import com.wzh.blog.infrastructure.redis.RedisChatEventBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisMessagingConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatEventBus chatEventBus,
            RedisAuthorizationInvalidationBus authorizationInvalidationBus) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatEventBus, new ChannelTopic(RedisChatEventBus.CHANNEL));
        container.addMessageListener(
                authorizationInvalidationBus, new ChannelTopic(RedisAuthorizationInvalidationBus.CHANNEL));
        return container;
    }
}
