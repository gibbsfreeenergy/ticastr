package com.wzh.blog.dto;

import com.wzh.blog.observability.CorrelationContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Versioned envelope for durable internal events. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DurableEventEnvelope<T> {

    private String eventId;
    private String eventType;
    private Integer version;
    private Instant occurredAt;
    private String aggregateId;
    private String traceId;
    private T payload;

    public static <T> DurableEventEnvelope<T> create(String eventType, String aggregateId, T payload) {
        return DurableEventEnvelope.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .version(1)
                .occurredAt(Instant.now())
                .aggregateId(aggregateId)
                .traceId(CorrelationContext.currentId())
                .payload(payload)
                .build();
    }
}
