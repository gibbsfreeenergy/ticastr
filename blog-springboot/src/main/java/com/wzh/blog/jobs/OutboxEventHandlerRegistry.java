package com.wzh.blog.jobs;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact event-type registry; duplicate registrations fail startup. */
@Component
public class OutboxEventHandlerRegistry {

    private final Map<String, OutboxEventHandler> handlers;

    public OutboxEventHandlerRegistry(List<OutboxEventHandler> handlerList) {
        Map<String, OutboxEventHandler> registered = new HashMap<>();
        for (OutboxEventHandler handler : handlerList) {
            if (handler.eventType() == null || handler.eventType().isBlank()) {
                throw new IllegalStateException("Outbox handler event type must not be blank");
            }
            if (registered.putIfAbsent(handler.eventType(), handler) != null) {
                throw new IllegalStateException("Duplicate outbox handler: " + handler.eventType());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public OutboxEventHandler get(String eventType) {
        return handlers.get(eventType);
    }

    public Map<String, OutboxEventHandler> all() {
        return handlers;
    }
}
