package com.wzh.blog.media;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.*;

class AssetLifecycleServiceTest {

    private final MediaAssetStore assetStore = mock(MediaAssetStore.class);
    private final MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
    private final AssetLifecycleService lifecycleService =
            new AssetLifecycleService(assetStore, referenceChecker);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void deletesDistinctReferencesOnlyAfterCommitWhenUnreferenced() {
        when(referenceChecker.isReferenced("old-cover")).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();

        lifecycleService.deleteAfterCommit(List.of("old-cover", "old-cover"));
        verifyNoInteractions(assetStore);

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCommit());

        verify(assetStore).delete("old-cover");
        verify(referenceChecker).isReferenced("old-cover");
    }

    @Test
    void compensatesUploadedAssetOnlyForRollback() {
        when(referenceChecker.isReferenced("voice")).thenReturn(false);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        lifecycleService.deleteAfterRollback("voice");
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verifyNoInteractions(assetStore);

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(assetStore).delete("voice");
    }
}
