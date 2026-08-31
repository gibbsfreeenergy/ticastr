package com.wzh.blog.infrastructure.redis;

import com.wzh.blog.config.RedisStreamProperties;
import com.wzh.blog.jobs.OutboxEventHandler;
import com.wzh.blog.jobs.OutboxEventHandlerRegistry;
import com.wzh.blog.service.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class RedisStreamConsumerTest {

    @Test
    void malformedStreamEntryIsDeadLetteredAndAcknowledgedWithoutTouchingOutbox() {
        RedisStreamEventTransport transport = mock(RedisStreamEventTransport.class);
        OutboxEventHandler handler = mock(OutboxEventHandler.class);
        OutboxEventHandlerRegistry handlers = mock(OutboxEventHandlerRegistry.class);
        OutboxEventService eventService = mock(OutboxEventService.class);
        RedisStreamProperties properties = new RedisStreamProperties();
        MapRecord<String, String, String> record = MapRecord.create(
                "ticastr:events:EMAIL_SEND", Map.of("eventType", "EMAIL_SEND", "payload", "{}"));

        when(handlers.all()).thenReturn(Map.of("EMAIL_SEND", handler));
        when(handlers.get("EMAIL_SEND")).thenReturn(handler);
        when(transport.claimIdle("EMAIL_SEND"))
                .thenReturn(List.of(new RedisStreamEventTransport.StreamDelivery(record, 1)));
        when(transport.read("EMAIL_SEND")).thenReturn(List.of());

        RedisStreamConsumer consumer = new RedisStreamConsumer(transport, handlers, eventService, properties);

        consumer.consume();

        verify(transport).publishDeadLetter(eq("EMAIL_SEND"), eq(record), anyString());
        verify(transport).acknowledge("EMAIL_SEND", record.getId());
        verifyNoInteractions(eventService, handler);
    }
}
