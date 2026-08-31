package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.springframework.stereotype.Service;

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
        metadataSource.clearDataSource();
        invalidationPublisher.publish();
    }
}
