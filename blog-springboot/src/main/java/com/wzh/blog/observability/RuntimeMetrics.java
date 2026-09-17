package com.wzh.blog.observability;

import com.wzh.blog.dao.OutboxEventDao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Small operational gauge for the durable content-event queue. */
@Component
public class RuntimeMetrics implements MeterBinder {

    private final OutboxEventDao outboxEventDao;

    public RuntimeMetrics(OutboxEventDao outboxEventDao) {
        this.outboxEventDao = outboxEventDao;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String status : new String[]{"PENDING", "PROCESSING", "DEAD"}) {
            Gauge.builder("ticastr.outbox.queue", outboxEventDao, dao -> count(dao, status))
                    .description("Durable content events by dispatch status")
                    .tag("status", status.toLowerCase())
                    .register(registry);
        }
    }

    private double count(OutboxEventDao dao, String status) {
        try {
            return dao.countByStatus(status);
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
