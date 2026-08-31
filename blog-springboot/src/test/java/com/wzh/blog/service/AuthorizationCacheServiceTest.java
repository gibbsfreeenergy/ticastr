package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthorizationCacheServiceTest {

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
