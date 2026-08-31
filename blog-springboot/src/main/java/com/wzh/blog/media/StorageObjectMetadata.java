package com.wzh.blog.media;

import java.time.Instant;
import java.util.Objects;

public record StorageObjectMetadata(
        String objectKey,
        String contentType,
        long sizeBytes,
        String checksum,
        Instant lastModified) {

    public StorageObjectMetadata {
        ObjectKeyPolicy.requireSafe(objectKey);
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Object size must not be negative");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("Object checksum must not be blank");
        }
        lastModified = Objects.requireNonNullElseGet(lastModified, Instant::now);
    }
}
