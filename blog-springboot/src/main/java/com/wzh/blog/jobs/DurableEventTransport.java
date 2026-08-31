package com.wzh.blog.jobs;

import com.wzh.blog.dto.DurableEventEnvelope;

/** Optional transport for already-durable outbox envelopes. */
public interface DurableEventTransport {

    boolean publish(DurableEventEnvelope<?> event);
}
