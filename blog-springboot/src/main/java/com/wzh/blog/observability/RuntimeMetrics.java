package com.wzh.blog.observability;

import com.wzh.blog.dao.OutboxEventDao;
import com.wzh.blog.service.ChatConnectionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Gauges that expose backlog and local real-time connection pressure. */
@Component
public class RuntimeMetrics implements MeterBinder {

    private final OutboxEventDao outboxEventDao;
    private final ChatConnectionRegistry connectionRegistry;

    public RuntimeMetrics(OutboxEventDao outboxEventDao, ChatConnectionRegistry connectionRegistry) {
        this.outboxEventDao = outboxEventDao;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registerOutboxGauge(registry, "PENDING");
        registerOutboxGauge(registry, "PROCESSING");
        registerOutboxGauge(registry, "DEAD");
        Gauge.builder("ticastr.websocket.connections", connectionRegistry, ChatConnectionRegistry::size)
                .description("Local WebSocket sessions connected to this API instance")
                .register(registry);
    }

    private void registerOutboxGauge(MeterRegistry registry, String status) {
        Gauge.builder("ticastr.outbox.events", outboxEventDao, dao -> count(dao, status))
                .description("Transactional outbox events by dispatch status")
                .tag("status", status.toLowerCase())
                .register(registry);
    }

    private double count(OutboxEventDao dao, String status) {
        try {
            return dao.countByStatus(status);
        } catch (RuntimeException exception) {
            // A metrics scrape must not make the API unhealthy when the
            // database is temporarily unavailable.
            return -1;
        }
    }
}
