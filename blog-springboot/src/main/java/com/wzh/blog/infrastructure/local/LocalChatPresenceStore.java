package com.wzh.blog.infrastructure.local;

import com.wzh.blog.service.ChatPresenceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local chat presence for the single-instance deployment mode. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalChatPresenceStore implements ChatPresenceStore {

    private static final long TTL_MILLIS = 90_000L;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    @Override
    public void register(String sessionId) {
        touch(sessionId);
    }

    @Override
    public void touch(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            cleanup();
            sessions.put(sessionId, System.currentTimeMillis());
        }
    }

    @Override
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        cleanup();
    }

    @Override
    public long count() {
        cleanup();
        return sessions.size();
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - TTL_MILLIS;
        sessions.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
