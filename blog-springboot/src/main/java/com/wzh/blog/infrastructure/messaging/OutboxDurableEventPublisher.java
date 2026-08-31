package com.wzh.blog.infrastructure.messaging;

import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.dto.EmailDTO;
import com.wzh.blog.service.DurableEventPublisher;
import com.wzh.blog.service.OutboxEventService;
import org.springframework.stereotype.Service;

/** Compatibility facade: email requests are durable outbox rows, never broker sends. */
@Service
public class OutboxDurableEventPublisher implements DurableEventPublisher {

    private final OutboxEventService outboxEventService;

    public OutboxDurableEventPublisher(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @Override
    public String publishEmail(EmailDTO email, String aggregateId) {
        return outboxEventService.enqueueEmail(email, aggregateId);
    }

    @Override
    public String publishEmail(DurableEventEnvelope<EmailDTO> event) {
        return outboxEventService.enqueue(
                // This compatibility entry point accepts legacy envelopes, but
                // email delivery must always resolve to the registered handler.
                "EMAIL_SEND",
                event.getVersion() == null ? 1 : event.getVersion(),
                event.getAggregateId(), event.getTraceId(), event.getPayload());
    }
}
