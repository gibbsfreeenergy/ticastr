package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Keeps the database-backed authorization map coherent across API replicas. */
@Service
public class AuthorizationCacheService {

    private final FilterInvocationSecurityMetadataSourceImpl metadataSource;
    private final AuthorizationInvalidationPublisher invalidationPublisher;

    public AuthorizationCacheService(FilterInvocationSecurityMetadataSourceImpl metadataSource,
                                     AuthorizationInvalidationPublisher invalidationPublisher) {
        this.metadataSource = metadataSource;
        this.invalidationPublisher = invalidationPublisher;
    }

    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidateNow();
                }
            });
            return;
        }
        invalidateNow();
    }

    private void invalidateNow() {
        metadataSource.clearDataSource();
        invalidationPublisher.publish();
    }
}
