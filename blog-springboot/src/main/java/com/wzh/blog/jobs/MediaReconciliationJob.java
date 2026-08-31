package com.wzh.blog.jobs;

import com.wzh.blog.media.AssetReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically requeues only a bounded page of failed media cleanup work. */
@Component
public class MediaReconciliationJob {

    private final AssetReconciliationService reconciliationService;

    public MediaReconciliationJob(AssetReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${app.media.reconciliation-interval-ms:60000}")
    public void reconcile() {
        reconciliationService.reconcile(50);
    }
}
