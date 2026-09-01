package com.wzh.blog.vo;

import java.time.LocalDateTime;

/** Safe persisted storage-usage snapshot. */
public record StorageUsageVO(
        String status,
        Long objectCount,
        Long bytes,
        LocalDateTime latestModified,
        LocalDateTime checkedAt,
        String error) {
}
