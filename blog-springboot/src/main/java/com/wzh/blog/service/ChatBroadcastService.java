package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import com.wzh.blog.service.impl.WebSocketServiceImpl;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.wzh.blog.enums.ChatTypeEnum.ONLINE_COUNT;
import static com.wzh.blog.enums.ChatTypeEnum.SEND_MESSAGE;
import static com.wzh.blog.enums.ChatTypeEnum.VOICE_MESSAGE;

/**
 * Redis-backed fan-out for chat events. Each API node only writes to its own
 * WebSocket sessions, while Redis distributes the event to every node.
 */
@Service
@Log4j2
public class ChatBroadcastService implements MessageListener {

    public static final String CHANNEL = "ticastr:chat:events";
    private static final String ONLINE_SESSIONS_KEY = "ticastr:chat:online-sessions";
    private static final long ONLINE_SESSION_TTL_MILLIS = 90_000;

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public ChatBroadcastService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(WebsocketMessageDTO messageDTO) {
        redisTemplate.convertAndSend(CHANNEL, JSON.toJSONString(messageDTO));
    }

    public void registerSession(String sessionId) {
        touchSession(sessionId);
        publishOnlineCount();
    }

    public void touchSession(String sessionId) {
        cleanupExpiredSessions();
        redisTemplate.opsForZSet().add(ONLINE_SESSIONS_KEY, sessionKey(sessionId), System.currentTimeMillis());
    }

    public void unregisterSession(String sessionId) {
        redisTemplate.opsForZSet().remove(ONLINE_SESSIONS_KEY, sessionKey(sessionId));
        cleanupExpiredSessions();
        publishOnlineCount();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebsocketMessageDTO messageDTO = JSON.parseObject(
                    new String(message.getBody(), StandardCharsets.UTF_8), WebsocketMessageDTO.class);
            if (messageDTO != null) {
                restoreChatRecord(messageDTO);
                WebSocketServiceImpl.broadcastLocal(messageDTO);
            }
        } catch (Exception e) {
            log.warn("Unable to deliver distributed chat event", e);
        }
    }

    private void publishOnlineCount() {
        cleanupExpiredSessions();
        Long count = redisTemplate.opsForZSet().zCard(ONLINE_SESSIONS_KEY);
        publish(WebsocketMessageDTO.builder()
                .type(ONLINE_COUNT.getType())
                .data(count == null ? 0 : count)
                .build());
    }

    private void cleanupExpiredSessions() {
        redisTemplate.opsForZSet().removeRangeByScore(
                ONLINE_SESSIONS_KEY, 0, System.currentTimeMillis() - ONLINE_SESSION_TTL_MILLIS);
    }

    static void restoreChatRecord(WebsocketMessageDTO messageDTO) {
        if (SEND_MESSAGE.getType().equals(messageDTO.getType())
                || VOICE_MESSAGE.getType().equals(messageDTO.getType())) {
            messageDTO.setData(JSON.parseObject(JSON.toJSONString(messageDTO.getData()), ChatRecord.class));
        }
    }

    private String sessionKey(String sessionId) {
        return instanceId + ":" + sessionId;
    }
}
