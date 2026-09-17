package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthorizationCacheServiceTest {

    @Test
    void waitsForCommitAndDoesNotInvalidateOnRollback() {
        var source = mock(FilterInvocationSecurityMetadataSourceImpl.class);
        var publisher = mock(AuthorizationInvalidationPublisher.class);
        var service = new AuthorizationCacheService(source, publisher);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.invalidate();
            verifyNoInteractions(source, publisher);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(source, publisher);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.invalidate();
            verifyNoInteractions(source, publisher);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(source).clearDataSource();
            verify(publisher).publish();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidatesLocallyAndPublishesToOtherNodes() {
        FilterInvocationSecurityMetadataSourceImpl metadataSource =
                mock(FilterInvocationSecurityMetadataSourceImpl.class);
        AuthorizationInvalidationPublisher publisher = mock(AuthorizationInvalidationPublisher.class);
        AuthorizationCacheService service = new AuthorizationCacheService(metadataSource, publisher);

        service.invalidate();

        verify(metadataSource).clearDataSource();
        verify(publisher).publish();
    }
}
