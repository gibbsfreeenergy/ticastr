package com.wzh.blog.infrastructure.redis;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.jobs.OutboxEventHandler;
import com.wzh.blog.jobs.OutboxEventHandlerRegistry;
import com.wzh.blog.service.OutboxEventService;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Redis Stream consumer group with ACK-after-handler and idle-entry recovery. */
@Service
@Log4j2
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisStreamConsumer {

    private final RedisStreamEventTransport transport;
    private final OutboxEventHandlerRegistry handlers;
    private final OutboxEventService eventService;
    private final com.wzh.blog.config.RedisStreamProperties properties;

    public RedisStreamConsumer(RedisStreamEventTransport transport,
                               OutboxEventHandlerRegistry handlers,
                               OutboxEventService eventService,
                               com.wzh.blog.config.RedisStreamProperties properties) {
        this.transport = transport;
        this.handlers = handlers;
        this.eventService = eventService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.redis.streams.consumer-interval-ms:1000}")
    public void consume() {
        for (String eventType : handlers.all().keySet()) {
            try {
                process(eventType, transport.claimIdle(eventType));
                process(eventType, transport.read(eventType));
            } catch (RuntimeException exception) {
                log.debug("Redis Stream polling unavailable for {}", eventType, exception);
                return;
            }
        }
    }

    private void process(String streamEventType, List<RedisStreamEventTransport.StreamDelivery> deliveries) {
        for (RedisStreamEventTransport.StreamDelivery delivery : deliveries) {
            var record = delivery.record();
            String eventId = record.getValue().get("eventId");
            try {
                DurableEventEnvelope<?> event = toEvent(record.getValue());
                if (!streamEventType.equals(event.getEventType())
                        || !event.getEventId().equals(eventId)) {
                    throw new IllegalArgumentException("Redis Stream event does not match its stream envelope");
                }
                OutboxEventHandler handler = handlers.get(event.getEventType());
                if (handler == null) {
                    throw new UnknownStreamEventException(event.getEventType());
                }
                if (!eventService.markProcessing(eventId)) {
                    // A database worker may have completed or permanently failed
                    // the same durable row while its stream copy was pending.
                    // The database state wins; discard only this duplicate copy.
                    transport.acknowledge(streamEventType, record.getId());
                    continue;
                }
                handler.handle(event);
                eventService.markPublished(eventId);
                transport.acknowledge(streamEventType, record.getId());
            } catch (Exception error) {
                handleFailure(streamEventType, delivery, eventId, error);
            }
        }
    }

    private void handleFailure(String streamEventType,
                               RedisStreamEventTransport.StreamDelivery delivery,
                               String eventId,
                               Exception error) {
        OutboxEventHandler handler = null;
        try {
            String type = delivery.record().getValue().get("eventType");
            handler = handlers.get(type);
        } catch (RuntimeException ignored) {
            // Unknown stream data is dead-lettered below.
        }
        if (eventId == null || eventId.isBlank()) {
            // There is no safe MySQL row to transition for malformed/orphaned
            // data. Preserve the record in the dead-letter stream, then ACK the
            // original so one corrupt entry cannot block the consumer group.
            transport.publishDeadLetter(streamEventType, delivery.record(), safeMessage(error));
            transport.acknowledge(streamEventType, delivery.record().getId());
            return;
        }
        boolean retryable = handler != null && handler.isRetryable(error)
                && !(error instanceof UnknownStreamEventException)
                && !(error instanceof IllegalArgumentException)
                && delivery.deliveryCount() < properties.getMaxDeliveryAttempts();
        if (retryable) {
            eventService.deferEnqueued(eventId, safeMessage(error));
            return;
        }
        transport.publishDeadLetter(streamEventType, delivery.record(), safeMessage(error));
        eventService.markFailed(eventId, safeMessage(error), false);
        transport.acknowledge(streamEventType, delivery.record().getId());
    }

    private DurableEventEnvelope<?> toEvent(Map<String, String> fields) {
        String eventId = fields.get("eventId");
        String eventType = fields.get("eventType");
        if (eventId == null || eventType == null || fields.get("payload") == null) {
            throw new IllegalArgumentException("Invalid Redis Stream event");
        }
        return DurableEventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .version(parseVersion(fields.get("version")))
                .aggregateId(blankToNull(fields.get("aggregateId")))
                .traceId(blankToNull(fields.get("traceId")))
                .occurredAt(parseInstant(fields.get("occurredAt")))
                .payload(JSON.parseObject(fields.get("payload"), Object.class))
                .build();
    }

    private Integer parseVersion(String value) {
        try {
            return value == null ? 1 : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static final class UnknownStreamEventException extends RuntimeException {
        private UnknownStreamEventException(String eventType) {
            super("Unknown stream event type: " + eventType);
        }
    }
}
