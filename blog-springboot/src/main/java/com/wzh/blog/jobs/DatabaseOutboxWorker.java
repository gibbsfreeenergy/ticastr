package com.wzh.blog.jobs;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.entity.OutboxEvent;
import com.wzh.blog.observability.EventContext;
import com.wzh.blog.service.OutboxEventService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Database fallback transport for durable events. Claiming is committed before
 * handler execution, so a crashed worker leaves a recoverable processing row.
 */
@Service
@Log4j2
public class DatabaseOutboxWorker {

    private final OutboxEventService eventService;
    private final OutboxEventHandlerRegistry handlers;
    private final OutboxMetrics metrics;

    public DatabaseOutboxWorker(OutboxEventService eventService,
                                OutboxEventHandlerRegistry handlers,
                                OutboxMetrics metrics) {
        this.eventService = eventService;
        this.handlers = handlers;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatch-interval-ms:5000}")
    public void dispatch() {
        for (OutboxEvent row : eventService.claimBatch()) {
            metrics.claimed();
            long started = System.nanoTime();
            OutboxEventHandler handler = null;
            try (var ignored = EventContext.open(row.getTraceId(), row.getEventId(), row.getEventType(), row.getAggregateId())) {
                DurableEventEnvelope<?> event = parse(row);
                handler = handlers.get(event.getEventType());
                if (handler == null) {
                    metrics.unknown();
                    throw new UnknownOutboxEventException(event.getEventType());
                }
                handler.handle(event);
                eventService.markPublished(row.getEventId());
                metrics.published();
            } catch (Exception error) {
                boolean retryable = handler != null && handler.isRetryable(error);
                if (error instanceof UnknownOutboxEventException) {
                    retryable = false;
                }
                eventService.markFailed(row.getEventId(), safeMessage(error), retryable);
                if (retryable) {
                    metrics.retried();
                } else {
                    metrics.dead();
                }
                log.error("Unable to process outbox event {} type {}", row.getEventId(), row.getEventType(), error);
            } finally {
                metrics.recordHandlerLatency(System.nanoTime() - started);
            }
        }
    }

    private DurableEventEnvelope<?> parse(OutboxEvent row) {
        DurableEventEnvelope<?> event = JSON.parseObject(row.getPayload(), DurableEventEnvelope.class);
        if (event == null || event.getEventId() == null || event.getEventType() == null || event.getPayload() == null) {
            throw new IllegalArgumentException("Invalid outbox event envelope");
        }
        return event;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static final class UnknownOutboxEventException extends RuntimeException {
        private UnknownOutboxEventException(String eventType) {
            super("Unknown outbox event type: " + eventType);
        }
    }
}
