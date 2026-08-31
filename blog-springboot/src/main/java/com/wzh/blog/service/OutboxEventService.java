package com.wzh.blog.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wzh.blog.dao.OutboxEventDao;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.dto.EmailDTO;
import com.wzh.blog.entity.OutboxEvent;
import com.wzh.blog.vo.OutboxEventAdminVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.web.PageQuery;
import com.wzh.blog.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stores durable events in the same database transaction as the business
 * change. Publishing is deliberately handled by the dispatcher below.
 */
@Service
public class OutboxEventService {

    public static final String PENDING = "PENDING";
    public static final String ENQUEUED = "ENQUEUED";
    public static final String PROCESSING = "PROCESSING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DEAD = "DEAD";

    private static final int MAX_ATTEMPTS = 8;
    private static final int CLAIM_BATCH_SIZE = 20;
    private static final Duration ENQUEUE_LEASE = Duration.ofMinutes(10);

    private final OutboxEventDao outboxEventDao;

    public OutboxEventService(OutboxEventDao outboxEventDao) {
        this.outboxEventDao = outboxEventDao;
    }

    @Transactional
    public String enqueueEmail(EmailDTO email, String aggregateId) {
        return enqueue("EMAIL_SEND", 1, aggregateId, null, email);
    }

    /** Creates the only durable-event shape used by business services. */
    @Transactional
    public String enqueue(String eventType, int version, String aggregateId, String traceId, Object payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Outbox event type must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Outbox event version must be positive");
        }
        DurableEventEnvelope<Object> envelope = DurableEventEnvelope.<Object>builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .eventType(eventType)
                .version(version)
                .occurredAt(Instant.now())
                .aggregateId(aggregateId)
                .traceId(traceId == null ? com.wzh.blog.observability.CorrelationContext.currentId() : traceId)
                .payload(payload)
                .build();
        LocalDateTime now = LocalDateTime.now();
        outboxEventDao.insert(OutboxEvent.builder()
                .eventId(envelope.getEventId())
                .eventType(envelope.getEventType())
                .eventVersion(envelope.getVersion())
                .aggregateId(envelope.getAggregateId())
                .payload(JSON.toJSONString(envelope))
                .traceId(envelope.getTraceId())
                .status(PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .enqueuedAt(now)
                .createdAt(now)
                .build());
        return envelope.getEventId();
    }

    @Transactional
    public String enqueueIfAbsent(String eventType, int version, String aggregateId, String traceId, Object payload) {
        if (outboxEventDao.existsOpen(eventType, aggregateId)) {
            return null;
        }
        return enqueue(eventType, version, aggregateId, traceId, payload);
    }

    @Transactional
    public List<OutboxEvent> claimBatch() {
        List<OutboxEvent> events = outboxEventDao.listClaimable(CLAIM_BATCH_SIZE);
        LocalDateTime now = LocalDateTime.now();
        events.forEach(event -> {
            event.setStatus(PROCESSING);
            event.setClaimedAt(now);
            event.setProcessingStartedAt(now);
            event.setAttempts(event.getAttempts() == null ? 1 : event.getAttempts() + 1);
            outboxEventDao.updateById(event);
        });
        return events;
    }

    /** Temporarily claims PENDING rows for Redis XADD without holding a DB transaction over the network call. */
    @Transactional
    public List<OutboxEvent> claimEnqueueBatch() {
        List<OutboxEvent> events = outboxEventDao.listEnqueueable(CLAIM_BATCH_SIZE);
        LocalDateTime now = LocalDateTime.now();
        events.forEach(event -> {
            event.setStatus(PROCESSING);
            event.setClaimedAt(now);
            event.setProcessingStartedAt(now);
            outboxEventDao.updateById(event);
        });
        return events;
    }

    @Transactional
    public void markEnqueued(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        LocalDateTime leaseUntil = LocalDateTime.now().plus(ENQUEUE_LEASE);
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .eq(OutboxEvent::getStatus, PROCESSING)
                .set(OutboxEvent::getStatus, ENQUEUED)
                .set(OutboxEvent::getEnqueuedAt, LocalDateTime.now())
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getNextAttemptAt, leaseUntil));
    }

    @Transactional
    public void releaseEnqueueClaim(String eventId, String error) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .eq(OutboxEvent::getStatus, PROCESSING)
                .set(OutboxEvent::getStatus, PENDING)
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getNextAttemptAt, LocalDateTime.now().plusSeconds(5))
                .set(OutboxEvent::getLastError, truncate(error)));
    }

    @Transactional
    public boolean markProcessing(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .in(OutboxEvent::getStatus, ENQUEUED, PROCESSING)
                .set(OutboxEvent::getStatus, PROCESSING)
                .set(OutboxEvent::getClaimedAt, LocalDateTime.now())
                .set(OutboxEvent::getProcessingStartedAt, LocalDateTime.now())) > 0;
    }

    @Transactional
    public void deferEnqueued(String eventId, String error) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .eq(OutboxEvent::getStatus, PROCESSING)
                .set(OutboxEvent::getStatus, ENQUEUED)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getNextAttemptAt, LocalDateTime.now().plus(ENQUEUE_LEASE))
                .set(OutboxEvent::getLastError, truncate(error)));
    }

    @Transactional
    public void markPublished(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .in(OutboxEvent::getStatus, PROCESSING, ENQUEUED)
                .set(OutboxEvent::getStatus, PUBLISHED)
                .set(OutboxEvent::getPublishedAt, LocalDateTime.now())
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getProcessedAt, LocalDateTime.now())
                .set(OutboxEvent::getLastError, null));
    }

    @Transactional
    public void markFailed(String eventId, String error) {
        markFailed(eventId, error, true);
    }

    @Transactional
    public void markFailed(String eventId, String error, boolean retryable) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        OutboxEvent event = outboxEventDao.selectById(eventId);
        if (event == null) {
            return;
        }
        int attempts = event.getAttempts() == null ? MAX_ATTEMPTS : event.getAttempts();
        boolean dead = !retryable || attempts >= MAX_ATTEMPTS;
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .in(OutboxEvent::getStatus, PROCESSING, ENQUEUED)
                .set(OutboxEvent::getStatus, dead ? DEAD : PENDING)
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getProcessedAt, dead ? LocalDateTime.now() : null)
                .set(OutboxEvent::getNextAttemptAt,
                        dead ? null : LocalDateTime.now().plus(backoff(attempts)))
                .set(OutboxEvent::getLastError, truncate(error)));
    }

    @Transactional
    public void retry(String eventId) {
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .in(OutboxEvent::getStatus, DEAD, ENQUEUED, PROCESSING)
                .set(OutboxEvent::getStatus, PENDING)
                .set(OutboxEvent::getAttempts, 0)
                .set(OutboxEvent::getNextAttemptAt, LocalDateTime.now())
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getProcessedAt, null)
                .set(OutboxEvent::getLastError, null));
    }

    public PageResult<OutboxEventAdminVO> list(PageQuery pageQuery) {
        List<OutboxEventAdminVO> rows = outboxEventDao.listRecent(pageQuery.offset(),
                        Math.toIntExact(pageQuery.size()))
                .stream()
                .map(this::toAdminView)
                .toList();
        return new PageResult<>(rows, Math.toIntExact(Math.min(Integer.MAX_VALUE, outboxEventDao.countAll())));
    }

    @Transactional
    public void retryFromAdmin(String eventId) {
        OutboxEvent event = outboxEventDao.selectById(eventId);
        if (event == null) {
            throw new NotFoundException("事件不存在");
        }
        outboxEventDao.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventId, eventId)
                .in(OutboxEvent::getStatus, DEAD, ENQUEUED, PROCESSING)
                .set(OutboxEvent::getStatus, PENDING)
                .set(OutboxEvent::getAttempts, 0)
                .set(OutboxEvent::getNextAttemptAt, LocalDateTime.now())
                .set(OutboxEvent::getClaimedAt, null)
                .set(OutboxEvent::getProcessingStartedAt, null)
                .set(OutboxEvent::getProcessedAt, null)
                .set(OutboxEvent::getLastError, null));
    }

    public java.util.Map<String, Long> metrics() {
        return java.util.Map.of(
                PENDING, outboxEventDao.countByStatus(PENDING),
                ENQUEUED, outboxEventDao.countByStatus(ENQUEUED),
                PROCESSING, outboxEventDao.countByStatus(PROCESSING),
                PUBLISHED, outboxEventDao.countByStatus(PUBLISHED),
                DEAD, outboxEventDao.countByStatus(DEAD));
    }

    private OutboxEventAdminVO toAdminView(OutboxEvent event) {
        return new OutboxEventAdminVO(event.getEventId(), event.getEventType(), event.getEventVersion(),
                event.getAggregateId(), event.getStatus(), event.getAttempts(), event.getNextAttemptAt(),
                event.getCreatedAt(), event.getPublishedAt(), event.getProcessedAt(), safeError(event.getLastError()));
    }

    private String safeError(String error) {
        if (error == null) {
            return null;
        }
        String normalized = error.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "…";
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(300, 1L << Math.min(attempts, 8));
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
