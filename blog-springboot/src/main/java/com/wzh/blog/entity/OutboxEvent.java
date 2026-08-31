package com.wzh.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Transactional outbox row. The payload is an immutable versioned event envelope. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_outbox_event")
public class OutboxEvent {

    @TableId(value = "event_id", type = IdType.INPUT)
    private String eventId;

    private String eventType;
    private Integer eventVersion;
    private String aggregateId;
    private String payload;
    private String traceId;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime claimedAt;
    private LocalDateTime enqueuedAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime processedAt;
    private String lastError;
}
