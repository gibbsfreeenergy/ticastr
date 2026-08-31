package com.wzh.blog.infrastructure.redis;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.entity.OutboxEvent;
import com.wzh.blog.jobs.DurableEventTransport;
import com.wzh.blog.service.OutboxEventService;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Moves durable outbox envelopes to Redis Streams without making Redis authoritative. */
@Service
@Log4j2
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisStreamBridgeJob {

    private final OutboxEventService eventService;
    private final DurableEventTransport transport;

    public RedisStreamBridgeJob(OutboxEventService eventService, DurableEventTransport transport) {
        this.eventService = eventService;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${app.redis.streams.bridge-interval-ms:1000}")
    public void dispatch() {
        for (OutboxEvent row : eventService.claimEnqueueBatch()) {
            try {
                DurableEventEnvelope<?> event = JSON.parseObject(row.getPayload(), DurableEventEnvelope.class);
                if (event == null || event.getEventType() == null || event.getEventId() == null) {
                    throw new IllegalArgumentException("Invalid outbox envelope");
                }
                if (!transport.publish(event)) {
                    throw new IllegalStateException("Redis Stream did not accept event");
                }
                eventService.markEnqueued(row.getEventId());
            } catch (RuntimeException exception) {
                eventService.releaseEnqueueClaim(row.getEventId(), exception.getMessage());
                log.warn("Redis Stream unavailable for outbox event {}", row.getEventId());
                // Leave the row for the database worker after a short retry delay.
                return;
            }
        }
    }
}
