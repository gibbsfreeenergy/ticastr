package com.wzh.blog.infrastructure.redis;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.config.RedisStreamProperties;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.jobs.DurableEventTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redis Streams adapter. Network access happens only from bridge/consumer calls. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisStreamEventTransport implements DurableEventTransport {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;

    public RedisStreamEventTransport(StringRedisTemplate redisTemplate, RedisStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean publish(DurableEventEnvelope<?> event) {
        MapRecord<String, String, String> record = MapRecord.create(
                properties.streamName(event.getEventType()), fields(event));
        return redisTemplate.opsForStream().add(record) != null;
    }

    public void ensureGroup(String eventType) {
        String stream = properties.streamName(eventType);
        try {
            ops().createGroup(stream, ReadOffset.from("0-0"), properties.getConsumerGroup());
        } catch (RedisSystemException exception) {
            if (!messageContains(exception, "BUSYGROUP")) {
                throw exception;
            }
        }
    }

    public List<StreamDelivery> read(String eventType) {
        String stream = properties.streamName(eventType);
        ensureGroup(eventType);
        List<MapRecord<String, String, String>> records = ops().read(
                Consumer.from(properties.getConsumerGroup(), properties.consumerName()),
                StreamReadOptions.empty()
                        .count(Math.max(1, properties.getBatchSize()))
                        .block(Duration.ofMillis(Math.max(1, properties.getPollTimeoutMillis()))),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        return toDeliveries(records, 1L);
    }

    /** Uses XCLAIM through the Spring Data API to recover idle pending entries. */
    public List<StreamDelivery> claimIdle(String eventType) {
        String stream = properties.streamName(eventType);
        ensureGroup(eventType);
        PendingMessages pending = ops().pending(stream, properties.getConsumerGroup(),
                Range.unbounded(), Math.max(1, properties.getBatchSize()));
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<RecordId> ids = new ArrayList<>();
        Map<String, Long> deliveries = new LinkedHashMap<>();
        for (PendingMessage message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().compareTo(
                    Duration.ofSeconds(Math.max(1, properties.getClaimTimeoutSeconds()))) >= 0) {
                ids.add(message.getId());
                deliveries.put(message.getIdAsString(), message.getTotalDeliveryCount());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<MapRecord<String, String, String>> records = ops().claim(stream,
                properties.getConsumerGroup(), properties.consumerName(),
                Duration.ofSeconds(Math.max(1, properties.getClaimTimeoutSeconds())),
                ids.toArray(RecordId[]::new));
        return records == null ? List.of() : records.stream()
                .map(record -> new StreamDelivery(record, deliveries.getOrDefault(record.getId().getValue(), 1L)))
                .toList();
    }

    public void acknowledge(String eventType, RecordId recordId) {
        ops().acknowledge(properties.streamName(eventType), properties.getConsumerGroup(), recordId);
    }

    public void publishDeadLetter(String eventType, MapRecord<String, String, String> record, String error) {
        Map<String, String> fields = new LinkedHashMap<>(record.getValue());
        fields.put("deadLetteredAt", Instant.now().toString());
        if (error != null && !error.isBlank()) {
            fields.put("error", error.length() <= 1000 ? error : error.substring(0, 1000));
        }
        ops().add(MapRecord.create(properties.deadLetterStreamName(eventType), fields));
    }

    public String streamName(String eventType) {
        return properties.streamName(eventType);
    }

    public long streamSize(String eventType) {
        Long size = ops().size(properties.streamName(eventType));
        return size == null ? 0L : size;
    }

    private StreamOperations<String, String, String> ops() {
        return redisTemplate.opsForStream();
    }

    private List<StreamDelivery> toDeliveries(List<MapRecord<String, String, String>> records, long count) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(record -> new StreamDelivery(record, count)).toList();
    }

    private Map<String, String> fields(DurableEventEnvelope<?> event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", event.getEventId());
        fields.put("eventType", event.getEventType());
        fields.put("version", String.valueOf(event.getVersion() == null ? 1 : event.getVersion()));
        fields.put("aggregateId", event.getAggregateId() == null ? "" : event.getAggregateId());
        fields.put("occurredAt", event.getOccurredAt() == null ? "" : event.getOccurredAt().toString());
        fields.put("traceId", event.getTraceId() == null ? "" : event.getTraceId());
        fields.put("payload", JSON.toJSONString(event.getPayload()));
        return fields;
    }

    private boolean messageContains(Exception exception, String text) {
        return exception.getMessage() != null && exception.getMessage().contains(text);
    }

    public record StreamDelivery(MapRecord<String, String, String> record, long deliveryCount) {
    }
}
