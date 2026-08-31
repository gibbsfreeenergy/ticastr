package com.wzh.blog.vo;

import java.time.LocalDateTime;

/** Safe operational projection of an outbox row; the payload is never exposed. */
public record OutboxEventAdminVO(
        String eventId,
        String eventType,
        Integer eventVersion,
        String aggregateId,
        String status,
        Integer attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        LocalDateTime processedAt,
        String lastError) {
}
