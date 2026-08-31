package com.wzh.blog.jobs;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.dto.EmailDTO;
import com.wzh.blog.observability.EventContext;
import com.wzh.blog.observability.CorrelationContext;
import com.wzh.blog.observability.OperationalMetrics;
import com.wzh.blog.service.EventDeduplicationStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Idempotent email handler for the MySQL outbox worker. */
@Component
public class EmailOutboxEventHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "EMAIL_SEND";
    private static final Duration DEDUPLICATION_RETENTION = Duration.ofDays(2);

    private final String senderAddress;
    private final JavaMailSender javaMailSender;
    private final EventDeduplicationStore deduplicationStore;
    private final OperationalMetrics metrics;

    public EmailOutboxEventHandler(@Value("${spring.mail.username:}") String senderAddress,
                                   JavaMailSender javaMailSender,
                                   EventDeduplicationStore deduplicationStore,
                                   OperationalMetrics metrics) {
        this.senderAddress = senderAddress;
        this.javaMailSender = javaMailSender;
        this.deduplicationStore = deduplicationStore;
        this.metrics = metrics;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DurableEventEnvelope<?> event) {
        if (!deduplicationStore.claim(event.getEventId(), DEDUPLICATION_RETENTION)) {
            if (!deduplicationStore.isCompleted(event.getEventId())) {
                throw new IllegalStateException("Email event is already being processed");
            }
            metrics.consumerDuplicate();
            return;
        }
        EmailDTO email = JSON.parseObject(JSON.toJSONString(event.getPayload()), EmailDTO.class);
        if (email == null || email.getEmail() == null || email.getEmail().isBlank()
                || email.getSubject() == null || email.getContent() == null) {
            deduplicationStore.release(event.getEventId());
            throw new IllegalArgumentException("Invalid email event payload");
        }
        try (CorrelationContext.Scope ignored = EventContext.open(event.getTraceId(), event.getEventId(),
                event.getEventType(), event.getAggregateId())) {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(senderAddress);
            mail.setTo(email.getEmail());
            mail.setSubject(email.getSubject());
            mail.setText(email.getContent());
            javaMailSender.send(mail);
            deduplicationStore.complete(event.getEventId(), DEDUPLICATION_RETENTION);
            metrics.consumerProcessed();
        } catch (RuntimeException error) {
            deduplicationStore.release(event.getEventId());
            metrics.consumerFailed();
            throw error;
        }
    }
}
