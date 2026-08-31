package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.config.RedisStreamProperties;
import com.wzh.blog.jobs.OutboxEventHandlerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only operational view of configured Redis Stream transport. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisStreamAdminService {

    private final RedisStreamEventTransport transport;
    private final RedisStreamProperties properties;
    private final OutboxEventHandlerRegistry handlers;

    public RedisStreamAdminService(RedisStreamEventTransport transport,
                                   RedisStreamProperties properties,
                                   OutboxEventHandlerRegistry handlers) {
        this.transport = transport;
        this.properties = properties;
        this.handlers = handlers;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String eventType : handlers.all().keySet()) {
            result.put(eventType, Map.of(
                    "stream", properties.streamName(eventType),
                    "size", transport.streamSize(eventType)));
        }
        return result;
    }
}
