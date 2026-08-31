package com.wzh.blog.service;

import java.time.Duration;

/** Idempotency port for durable consumers. */
public interface EventDeduplicationStore {

    boolean claim(String eventId, Duration retention);

    /** Distinguishes a completed duplicate from an event still being processed. */
    boolean isCompleted(String eventId);

    void complete(String eventId, Duration retention);

    void release(String eventId);
}
