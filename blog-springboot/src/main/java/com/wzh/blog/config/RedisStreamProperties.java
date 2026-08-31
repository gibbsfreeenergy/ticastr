package com.wzh.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded Redis Stream transport settings. */
@Data
@ConfigurationProperties(prefix = "app.redis.streams")
public class RedisStreamProperties {

    private String prefix = "ticastr";
    private String consumerGroup = "ticastr-workers-v1";
    private String consumerName;
    private int batchSize = 20;
    private long claimTimeoutSeconds = 600;
    private int maxDeliveryAttempts = 8;
    private long pollTimeoutMillis = 500;

    public String consumerName() {
        return consumerName == null || consumerName.isBlank()
                ? "worker-" + Integer.toHexString(System.identityHashCode(this))
                : consumerName;
    }

    public String streamName(String eventType) {
        return prefix + ":events:" + safeEventType(eventType);
    }

    public String deadLetterStreamName(String eventType) {
        return prefix + ":dead:" + safeEventType(eventType);
    }

    private String safeEventType(String eventType) {
        if (eventType == null || eventType.isBlank() || !eventType.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Invalid event type for Redis Stream");
        }
        return eventType;
    }
}
