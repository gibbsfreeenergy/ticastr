package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * websocket消息
 *
 * @author yezhiqiu
 * @date 2021/08/01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebsocketMessageDTO {

    /**
     * 类型
     */
    private Integer type;

    /** Client-visible event id used for duplicate detection and diagnostics. */
    private String eventId;

    /** Version of the WebSocket message envelope. */
    private Integer version;

    /** Stable server timestamp used by reconnecting clients and diagnostics. */
    private Instant serverTime;

    /** Client-generated id reused across retries of the same send. */
    private String clientMessageId;

    /** Durable chat record id for message events and acknowledgements. */
    private Integer messageId;

    /**
     * 数据
     */
    private Object data;

}
