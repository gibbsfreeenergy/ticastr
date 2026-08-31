package com.wzh.blog.infrastructure.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded LRU/TTL cache used when Redis is disabled or unavailable. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InProcessCacheStore implements CacheStore {

    private static final int MAX_ENTRIES = 2_048;
    private final Map<String, Entry> entries = new LinkedHashMap<>(128, 0.75f, true);

    @Override
    public synchronized Object get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public synchronized void put(String key, Object value, Duration ttl) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        long ttlMillis = ttl == null ? 0L : ttl.toMillis();
        if (ttlMillis <= 0) {
            evict(key);
            return;
        }
        cleanupExpired();
        entries.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    @Override
    public synchronized void evict(String key) {
        entries.remove(key);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt() <= now) {
                iterator.remove();
            }
        }
    }

    private record Entry(Object value, long expiresAt) {
    }
}
