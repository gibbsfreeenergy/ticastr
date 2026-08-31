package com.wzh.blog.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Low-cardinality counters for cross-system operations. */
@Component
public class OperationalMetrics {

    private final Counter outboxPublished;
    private final Counter outboxFailed;
    private final Counter chatPublished;
    private final Counter chatDelivered;
    private final Counter chatFailed;
    private final Counter consumerProcessed;
    private final Counter consumerDuplicate;
    private final Counter consumerFailed;
    private final Timer consumerProcessing;

    public OperationalMetrics(MeterRegistry registry) {
        outboxPublished = counter(registry, "outbox", "published");
        outboxFailed = counter(registry, "outbox", "failed");
        chatPublished = counter(registry, "chat", "published");
        chatDelivered = counter(registry, "chat", "delivered");
        chatFailed = counter(registry, "chat", "failed");
        consumerProcessed = counter(registry, "consumer", "processed");
        consumerDuplicate = counter(registry, "consumer", "duplicate");
        consumerFailed = counter(registry, "consumer", "failed");
        consumerProcessing = Timer.builder("ticastr.consumer.processing")
                .description("Durable consumer processing time")
                .tag("consumer", "durable")
                .register(registry);
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void outboxFailed() {
        outboxFailed.increment();
    }

    public void chatPublished() {
        chatPublished.increment();
    }

    public void chatDelivered() {
        chatDelivered.increment();
    }

    public void chatFailed() {
        chatFailed.increment();
    }

    public void consumerProcessed() {
        consumerProcessed.increment();
    }

    public void consumerDuplicate() {
        consumerDuplicate.increment();
    }

    public void consumerFailed() {
        consumerFailed.increment();
    }

    public void consumerProcessingNanos(long nanos) {
        consumerProcessing.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private Counter counter(MeterRegistry registry, String boundary, String outcome) {
        return Counter.builder("ticastr.operations")
                .description("Cross-system operation outcomes")
                .tag("boundary", boundary)
                .tag("outcome", outcome)
                .register(registry);
    }
}
