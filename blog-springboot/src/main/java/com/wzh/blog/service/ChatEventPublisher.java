package com.wzh.blog.service;

import com.wzh.blog.dto.WebsocketMessageDTO;

/** Best-effort, real-time chat event port. It is not a durable event bus. */
public interface ChatEventPublisher {

    void publish(WebsocketMessageDTO message);
}
