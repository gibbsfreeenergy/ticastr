package com.wzh.blog.media;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import com.wzh.blog.service.OutboxEventService;

/**
 * Coordinates database references and external objects. Deletion is deferred
 * until commit so a rolled-back database operation cannot remove a live asset.
 */
@Service
@Log4j2
public class AssetLifecycleService {

    private final MediaAssetStore assetStore;
    private final MediaReferenceChecker referenceChecker;
    private final MediaAssetLedger assetLedger;
    private final OutboxEventService outboxEventService;

    /** Compatibility constructor for focused unit tests and legacy callers. */
    public AssetLifecycleService(MediaAssetStore assetStore, MediaReferenceChecker referenceChecker) {
        this(assetStore, referenceChecker, null, null);
    }

    public AssetLifecycleService(MediaAssetStore assetStore,
                                 MediaReferenceChecker referenceChecker,
                                 MediaAssetLedger assetLedger) {
        this(assetStore, referenceChecker, assetLedger, null);
    }

    @Autowired
    public AssetLifecycleService(MediaAssetStore assetStore,
                                 MediaReferenceChecker referenceChecker,
                                 MediaAssetLedger assetLedger,
                                 OutboxEventService outboxEventService) {
        this.assetStore = assetStore;
        this.referenceChecker = referenceChecker;
        this.assetLedger = assetLedger;
        this.outboxEventService = outboxEventService;
    }

    public void deleteAfterCommit(Collection<String> fileReferences) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        if (fileReferences != null) {
            fileReferences.stream()
                    .filter(reference -> reference != null && !reference.isBlank())
                    .forEach(references::add);
        }
        if (references.isEmpty()) {
            return;
        }
        Runnable delete = () -> references.forEach(this::enqueueDelete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    /** Removes an upload only when the surrounding business transaction rolls back. */
    public void deleteAfterRollback(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        enqueueDelete(fileReference);
                    }
                }
            });
        }
    }

    private void deleteSafely(String reference) {
        try {
            if (referenceChecker.isReferenced(reference)) {
                if (assetLedger != null) {
                    assetLedger.register(reference, reference, "unknown");
                }
                log.info("Retaining media asset {} because another record still references it", reference);
                return;
            }
            if (assetLedger != null) {
                assetLedger.markDeletionStarted(reference);
            }
            assetStore.delete(reference);
            if (assetLedger != null) {
                assetLedger.markDeleted(reference);
            }
        } catch (RuntimeException exception) {
            if (assetLedger != null) {
                assetLedger.markDeletionFailed(reference, exception.getMessage());
            }
            // The DB reference is already gone. Keep the failure visible so a
            // reconciliation job can report it without rolling back the user action.
            log.error("Unable to delete media asset reference {}", reference, exception);
        }
    }

    private void enqueueDelete(String reference) {
        if (outboxEventService == null) {
            deleteSafely(reference);
            return;
        }
        outboxEventService.enqueue("MEDIA_DELETE", 1, reference, null,
                Map.of("reference", reference));
    }
}
