package com.wzh.blog.infrastructure.local;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import com.wzh.blog.service.AuthorizationInvalidationPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Clears authorization metadata locally when Redis fan-out is disabled. */
@Service
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalAuthorizationInvalidationPublisher implements AuthorizationInvalidationPublisher {

    private final FilterInvocationSecurityMetadataSourceImpl metadataSource;

    public LocalAuthorizationInvalidationPublisher(FilterInvocationSecurityMetadataSourceImpl metadataSource) {
        this.metadataSource = metadataSource;
    }

    @Override
    public void publish() {
        metadataSource.clearDataSource();
    }
}
