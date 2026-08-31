package com.wzh.blog.media;

import java.time.Instant;

public record StorageUsage(long objectCount, long totalBytes, Instant latestObjectModified) {

    public StorageUsage {
        if (objectCount < 0) {
            throw new IllegalArgumentException("Object count must not be negative");
        }
        if (totalBytes < 0) {
            throw new IllegalArgumentException("Total bytes must not be negative");
        }
    }
}
