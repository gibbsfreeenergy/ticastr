package com.wzh.blog.jobs;

import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.media.MediaAssetLedger;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.media.MediaReferenceChecker;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Idempotent, reference-aware deletion of externally stored media. */
@Component
public class MediaCleanupHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "MEDIA_DELETE";

    private final MediaAssetStore assetStore;
    private final MediaReferenceChecker referenceChecker;
    private final MediaAssetLedger assetLedger;

    public MediaCleanupHandler(MediaAssetStore assetStore,
                               MediaReferenceChecker referenceChecker,
                               MediaAssetLedger assetLedger) {
        this.assetStore = assetStore;
        this.referenceChecker = referenceChecker;
        this.assetLedger = assetLedger;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DurableEventEnvelope<?> event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)
                || !(payload.get("reference") instanceof String reference)
                || reference.isBlank()) {
            throw new IllegalArgumentException("Invalid media delete payload");
        }
        if (referenceChecker.isReferenced(reference)) {
            return;
        }
        assetLedger.markDeletionStarted(reference);
        try {
            // Provider adapters must treat delete of a missing object as success.
            assetStore.delete(reference);
            assetLedger.markDeleted(reference);
        } catch (RuntimeException exception) {
            assetLedger.markDeletionFailed(reference, exception.getMessage());
            throw exception;
        }
    }
}
