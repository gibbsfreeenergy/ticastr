package com.wzh.blog.config;

import com.wzh.blog.service.AuthorizationCacheService;
import com.wzh.blog.service.ChatBroadcastService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisMessagingConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatBroadcastService chatBroadcastService,
            AuthorizationCacheService authorizationCacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatBroadcastService, new ChannelTopic(ChatBroadcastService.CHANNEL));
        container.addMessageListener(
                authorizationCacheService, new ChannelTopic(AuthorizationCacheService.CHANNEL));
        return container;
    }
}
