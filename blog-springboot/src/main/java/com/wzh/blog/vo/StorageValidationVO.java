package com.wzh.blog.vo;

import java.time.LocalDateTime;

/** Safe persisted validation snapshot for a managed storage profile. */
public record StorageValidationVO(
        String status,
        boolean success,
        LocalDateTime validatedAt,
        String message) {
}
