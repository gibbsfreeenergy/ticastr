package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.wzh.blog.enums.ChatTypeEnum.ONLINE_COUNT;
import static com.wzh.blog.enums.ChatTypeEnum.SEND_MESSAGE;
import static com.wzh.blog.enums.ChatTypeEnum.VOICE_MESSAGE;

import java.time.Instant;
import java.util.UUID;

/**
 * Redis-backed fan-out for chat events. Each API node only writes to its own
 * WebSocket sessions, while Redis distributes the event to every node.
 */
@Service
@Log4j2
public class ChatBroadcastService {

    private final ChatPresenceStore presenceStore;
    private final ChatEventPublisher eventPublisher;

    public ChatBroadcastService(ChatPresenceStore presenceStore, ChatEventPublisher eventPublisher) {
        this.presenceStore = presenceStore;
        this.eventPublisher = eventPublisher;
    }

    public void publish(WebsocketMessageDTO messageDTO) {
        eventPublisher.publish(messageDTO);
    }

    /** Avoids broadcasting a database event that is later rolled back. */
    public void publishAfterCommit(WebsocketMessageDTO messageDTO) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            publish(messageDTO);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(messageDTO);
            }
        });
    }

    public void registerSession(String sessionId) {
        presenceStore.register(sessionId);
        publishOnlineCount();
    }

    public void touchSession(String sessionId) {
        presenceStore.touch(sessionId);
    }

    public void unregisterSession(String sessionId) {
        presenceStore.unregister(sessionId);
        publishOnlineCount();
    }

    private void publishOnlineCount() {
        long count = presenceStore.count();
        publish(WebsocketMessageDTO.builder()
                .type(ONLINE_COUNT.getType())
                .eventId(UUID.randomUUID().toString())
                .version(1)
                .serverTime(Instant.now())
                .data(count)
                .build());
    }

    public static void restoreChatRecord(WebsocketMessageDTO messageDTO) {
        if (SEND_MESSAGE.getType().equals(messageDTO.getType())
                || VOICE_MESSAGE.getType().equals(messageDTO.getType())) {
            messageDTO.setData(JSON.parseObject(JSON.toJSONString(messageDTO.getData()), ChatRecord.class));
        }
    }

}
