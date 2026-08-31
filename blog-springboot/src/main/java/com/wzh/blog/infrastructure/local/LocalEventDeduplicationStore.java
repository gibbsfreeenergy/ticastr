package com.wzh.blog.infrastructure.local;

import com.wzh.blog.service.EventDeduplicationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded-time event idempotency store for the no-Redis mode. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEventDeduplicationStore implements EventDeduplicationStore {

    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean claim(String eventId, Duration retention) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        cleanup();
        long expiresAt = System.currentTimeMillis() + PROCESSING_LEASE.toMillis();
        Entry previous = entries.putIfAbsent(eventId, new Entry(false, expiresAt));
        return previous == null;
    }

    @Override
    public boolean isCompleted(String eventId) {
        cleanup();
        Entry entry = entries.get(eventId);
        return entry != null && entry.completed();
    }

    @Override
    public void complete(String eventId, Duration retention) {
        if (eventId != null && !eventId.isBlank()) {
            Duration safeRetention = retention == null || retention.isNegative() || retention.isZero()
                    ? PROCESSING_LEASE
                    : retention;
            entries.put(eventId, new Entry(true, System.currentTimeMillis() + safeRetention.toMillis()));
        }
    }

    @Override
    public void release(String eventId) {
        entries.remove(eventId);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Entry(boolean completed, long expiresAt) {
    }
}
