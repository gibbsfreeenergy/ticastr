package com.wzh.blog.strategy.context;

import com.wzh.blog.config.StorageProperties;
import com.wzh.blog.media.MediaAssetLedger;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.security.UploadValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UploadStrategyContextTest {

    @Test
    void deletesUploadedObjectWhenAssetLedgerRegistrationFails() throws Exception {
        StorageProvider provider = mock(StorageProvider.class);
        when(provider.type()).thenReturn(StorageProviderType.LOCAL);
        when(provider.put(anyString(), any(), anyLong(), eq("image/png")))
                .thenAnswer(invocation -> new StorageObjectMetadata(
                        invocation.getArgument(0), "image/png", 3, "checksum", Instant.now()));
        MediaAssetLedger ledger = mock(MediaAssetLedger.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(ledger).register(anyString(), anyString(), eq("local"));
        StorageProperties properties = new StorageProperties();
        properties.setLocalPublicUrl("/uploads/");
        StorageProviderRegistry registry = new StorageProviderRegistry(
                StorageProviderType.LOCAL, List.of(provider), properties);
        UploadValidationService validation = mock(UploadValidationService.class);
        when(validation.extension(any())).thenReturn(".png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1, 2, 3});

        UploadStrategyContext context = new UploadStrategyContext(registry, ledger, validation);

        assertThatThrownBy(() -> context.upload(file, "articles/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(provider).delete(anyString());
    }
}
