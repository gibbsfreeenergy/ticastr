package com.wzh.blog.service;

import com.wzh.blog.handler.FilterInvocationSecurityMetadataSourceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthorizationCacheServiceTest {

    @Test
    void invalidatesLocallyAndPublishesToOtherNodes() {
        FilterInvocationSecurityMetadataSourceImpl metadataSource =
                mock(FilterInvocationSecurityMetadataSourceImpl.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AuthorizationCacheService service = new AuthorizationCacheService(metadataSource, redisTemplate);

        service.invalidate();

        verify(metadataSource).clearDataSource();
        verify(redisTemplate).convertAndSend(AuthorizationCacheService.CHANNEL, "invalidate");
    }

    @Test
    void clearsTheLocalCacheWhenAnotherNodePublishes() {
        FilterInvocationSecurityMetadataSourceImpl metadataSource =
                mock(FilterInvocationSecurityMetadataSourceImpl.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AuthorizationCacheService service = new AuthorizationCacheService(metadataSource, redisTemplate);

        service.onMessage(mock(Message.class), new byte[0]);

        verify(metadataSource).clearDataSource();
        verifyNoInteractions(redisTemplate);
    }
}
