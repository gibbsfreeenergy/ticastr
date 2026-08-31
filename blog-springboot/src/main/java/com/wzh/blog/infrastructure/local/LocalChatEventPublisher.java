package com.wzh.blog.infrastructure.local;

import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.service.ChatBroadcastService;
import com.wzh.blog.service.ChatConnectionRegistry;
import com.wzh.blog.service.ChatEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Delivers chat events to the WebSocket registry in a single JVM. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalChatEventPublisher implements ChatEventPublisher {

    private final ChatConnectionRegistry connectionRegistry;

    public LocalChatEventPublisher(ChatConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void publish(WebsocketMessageDTO message) {
        ChatBroadcastService.restoreChatRecord(message);
        connectionRegistry.broadcast(message);
    }
}
