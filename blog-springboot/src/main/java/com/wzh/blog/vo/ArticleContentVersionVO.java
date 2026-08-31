package com.wzh.blog.vo;

import java.time.LocalDateTime;

/** Non-body metadata for one immutable Markdown asset version. */
public record ArticleContentVersionVO(
        String assetId,
        Integer articleId,
        Integer version,
        String contentType,
        Long sizeBytes,
        String checksum,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
