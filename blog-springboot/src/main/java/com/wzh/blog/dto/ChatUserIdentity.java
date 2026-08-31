package com.wzh.blog.dto;

/** Identity and request metadata resolved by the WebSocket adapter. */
public record ChatUserIdentity(
        Integer userId,
        String nickname,
        String avatar,
        String ipAddress,
        String ipSource,
        String clientToken) {
}
