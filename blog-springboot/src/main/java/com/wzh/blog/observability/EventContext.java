package com.wzh.blog.observability;

import org.slf4j.MDC;

/** Adds durable-event fields to the current correlation scope for logging. */
public final class EventContext {

    private EventContext() {
    }

    public static CorrelationContext.Scope open(String traceId,
                                                String eventId,
                                                String eventType,
                                                String aggregateId) {
        CorrelationContext.Scope scope = CorrelationContext.open(traceId);
        put("eventId", eventId);
        put("eventType", eventType);
        put("aggregateId", aggregateId);
        return scope;
    }

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
