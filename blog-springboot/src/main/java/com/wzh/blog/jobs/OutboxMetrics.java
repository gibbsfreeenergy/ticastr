package com.wzh.blog.jobs;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Low-cardinality metrics for the database outbox worker. */
@Component
public class OutboxMetrics {

    private final Counter claimed;
    private final Counter published;
    private final Counter retried;
    private final Counter dead;
    private final Counter unknown;
    private final Timer handlerLatency;

    public OutboxMetrics(MeterRegistry registry) {
        claimed = counter(registry, "claimed");
        published = counter(registry, "published");
        retried = counter(registry, "retried");
        dead = counter(registry, "dead");
        unknown = counter(registry, "unknown");
        handlerLatency = Timer.builder("ticastr.outbox.handler.latency")
                .description("Outbox handler execution latency")
                .register(registry);
    }

    public void claimed() { claimed.increment(); }

    public void published() { published.increment(); }

    public void retried() { retried.increment(); }

    public void dead() { dead.increment(); }

    public void unknown() { unknown.increment(); }

    public void recordHandlerLatency(long nanos) {
        handlerLatency.record(nanos, TimeUnit.NANOSECONDS);
    }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("ticastr.outbox.events")
                .tag("outcome", outcome)
                .register(registry);
    }
}
