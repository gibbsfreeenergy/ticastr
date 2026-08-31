package com.wzh.blog.jobs;

import com.wzh.blog.dto.DurableEventEnvelope;

/** Handles one exact, versioned durable event type. */
public interface OutboxEventHandler {

    String eventType();

    void handle(DurableEventEnvelope<?> event) throws Exception;

    default boolean isRetryable(Exception error) {
        return true;
    }
}
