package com.wzh.blog.jobs;

import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.content.ContentAssetPersistenceService;
import com.wzh.blog.service.OutboxEventService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Requeues retired content assets in bounded pages. */
@Component
public class ContentAssetReconciliationJob {

    private final ContentAssetPersistenceService persistence;
    private final OutboxEventService outboxEventService;

    public ContentAssetReconciliationJob(ContentAssetPersistenceService persistence,
                                         OutboxEventService outboxEventService) {
        this.persistence = persistence;
        this.outboxEventService = outboxEventService;
    }

    @Scheduled(fixedDelayString = "${app.media.content-reconciliation-interval-ms:60000}")
    public void reconcile() {
        for (ContentAsset asset : persistence.listCleanupCandidates(50)) {
            outboxEventService.enqueueIfAbsent(
                    ContentAssetCleanupHandler.EVENT_TYPE, 1, asset.getAssetId(), null,
                    Map.of("assetId", asset.getAssetId()));
        }
    }
}
