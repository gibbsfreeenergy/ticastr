package com.wzh.blog.jobs;

import com.wzh.blog.dto.DurableEventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit no-op transport used when Redis Streams are disabled. */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class NoopDurableEventTransport implements DurableEventTransport {

    @Override
    public boolean publish(DurableEventEnvelope<?> event) {
        return false;
    }
}
