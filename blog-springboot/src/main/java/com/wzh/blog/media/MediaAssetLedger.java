package com.wzh.blog.media;

import java.time.LocalDateTime;
import java.util.List;

/** Records external media ownership independently from business URL columns. */
public interface MediaAssetLedger {

    record MediaAssetLocation(String provider, String objectKey) {
    }

    record MediaAssetRecord(String reference, String provider, String objectKey,
                            String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    void register(String reference, String objectKey, String storageMode);

    void markDeletionStarted(String reference);

    void markDeleted(String reference);

    void markDeletionFailed(String reference, String reason);

    /** Resolves provider identity for deletion after an active provider switch. */
    default MediaAssetLocation locationFor(String reference) {
        return null;
    }

    default List<MediaAssetRecord> listCleanupCandidates(int limit) {
        return List.of();
    }
}
