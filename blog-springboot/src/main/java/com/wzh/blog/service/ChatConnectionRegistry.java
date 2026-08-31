package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.WebsocketMessageDTO;
import com.wzh.blog.entity.ChatRecord;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Instance-local WebSocket connection registry. */
@Service
@Log4j2
public class ChatConnectionRegistry {

    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();

    public void register(Session session, String clientToken) {
        connections.put(session.getId(), new Connection(session, clientToken));
    }

    public void unregister(String sessionId) {
        connections.remove(sessionId);
    }

    public int size() {
        return connections.size();
    }

    public void broadcast(WebsocketMessageDTO message) {
        connections.values().removeIf(connection -> !connection.send(message));
    }

    private record Connection(Session session, String clientToken) {

        private boolean send(WebsocketMessageDTO message) {
            if (session == null || !session.isOpen()) {
                return false;
            }
            try {
                WebsocketMessageDTO outbound = message;
                if (message.getData() instanceof ChatRecord chatRecord) {
                    ChatRecord visibleRecord = JSON.parseObject(JSON.toJSONString(chatRecord), ChatRecord.class);
                    visibleRecord.setOwner(Objects.equals(chatRecord.getClientToken(), clientToken));
                    outbound = WebsocketMessageDTO.builder()
                            .type(message.getType())
                            .eventId(message.getEventId())
                            .version(message.getVersion())
                            .serverTime(message.getServerTime())
                            .clientMessageId(message.getClientMessageId())
                            .messageId(message.getMessageId())
                            .data(visibleRecord)
                            .build();
                }
                synchronized (session) {
                    session.getBasicRemote().sendText(JSON.toJSONString(outbound));
                }
                return true;
            } catch (IOException | RuntimeException exception) {
                log.warn("Unable to deliver chat event to local WebSocket session {}", session.getId(), exception);
                return false;
            }
        }
    }
}
