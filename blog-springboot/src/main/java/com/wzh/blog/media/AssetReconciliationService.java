package com.wzh.blog.media;

import com.wzh.blog.service.OutboxEventService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Re-enqueues bounded, failed cleanup work without calling providers in SQL transactions. */
@Service
public class AssetReconciliationService {

    private final MediaAssetLedger assetLedger;
    private final MediaReferenceChecker referenceChecker;
    private final OutboxEventService outboxEventService;

    public AssetReconciliationService(MediaAssetLedger assetLedger,
                                      MediaReferenceChecker referenceChecker,
                                      OutboxEventService outboxEventService) {
        this.assetLedger = assetLedger;
        this.referenceChecker = referenceChecker;
        this.outboxEventService = outboxEventService;
    }

    public int reconcile(int limit) {
        int enqueued = 0;
        for (MediaAssetLedger.MediaAssetRecord candidate : assetLedger.listCleanupCandidates(limit)) {
            if (candidate.reference() == null || referenceChecker.isReferenced(candidate.reference())) {
                continue;
            }
            String eventId = outboxEventService.enqueueIfAbsent(
                    "MEDIA_DELETE", 1, candidate.reference(), null,
                    Map.of("reference", candidate.reference()));
            if (eventId != null) {
                enqueued++;
            }
        }
        return enqueued;
    }
}
