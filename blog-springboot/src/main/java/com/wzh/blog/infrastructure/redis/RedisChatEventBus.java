package com.wzh.blog.infrastructure.redis;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.service.ChatConnectionRegistry;
import com.wzh.blog.service.ChatEventPublisher;
import com.wzh.blog.service.ChatBroadcastService;
import com.wzh.blog.observability.OperationalMetrics;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Redis Pub/Sub adapter for best-effort chat fan-out. */
@Service
@Log4j2
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisChatEventBus implements ChatEventPublisher, MessageListener {

    public static final String CHANNEL = "ticastr:chat:events:v1";

    private final StringRedisTemplate redisTemplate;
    private final ChatConnectionRegistry connectionRegistry;
    private final OperationalMetrics metrics;

    public RedisChatEventBus(StringRedisTemplate redisTemplate,
                             ChatConnectionRegistry connectionRegistry,
                             OperationalMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.connectionRegistry = connectionRegistry;
        this.metrics = metrics;
    }

    @Override
    public void publish(WebsocketMessageDTO message) {
        try {
            redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(message));
            metrics.chatPublished();
        } catch (RuntimeException exception) {
            metrics.chatFailed();
            throw exception;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebsocketMessageDTO messageDTO = JSON.parseObject(
                    new String(message.getBody(), StandardCharsets.UTF_8), WebsocketMessageDTO.class);
            if (messageDTO != null) {
                ChatBroadcastService.restoreChatRecord(messageDTO);
                connectionRegistry.broadcast(messageDTO);
                metrics.chatDelivered();
            }
        } catch (RuntimeException exception) {
            metrics.chatFailed();
            log.warn("Unable to deliver distributed chat event", exception);
        }
    }
}
