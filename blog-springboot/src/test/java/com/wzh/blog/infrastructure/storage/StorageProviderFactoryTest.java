package com.wzh.blog.infrastructure.storage;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderConfigSnapshot;
import com.wzh.blog.media.StorageProviderType;
import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StorageProviderFactoryTest {

    @Test
    void managedFactoryCreationDoesNotDecryptCredentials() {
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        StorageProviderFactory factory = new StorageProviderFactory(crypto);

        StorageProvider provider = factory.create(cloudConfig());

        assertTrue(provider.configured());
        assertFalse(provider.toString().contains("cipher-id"));
        assertFalse(provider.toString().contains("cipher-secret"));
        verifyNoInteractions(crypto);
    }

    @Test
    void managedProviderResolvesCredentialsOnlyWhenCreatingItsSdkClient() throws Exception {
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.decrypt("cipher-id")).thenReturn("access-id");
        when(crypto.decrypt("cipher-secret")).thenReturn("access-secret");
        AtomicInteger clientCreations = new AtomicInteger();
        OSS client = mock(OSS.class);
        StorageProviderConfigSnapshot profile = StorageProviderConfigSnapshot.managed(
                7L, "oss-profile", StorageProviderType.OSS, "https://oss.example.test", "blog-assets",
                "cn-shanghai", null, "https://cdn.example.test", new StorageProviderConfigSnapshot.CredentialSupplier() {
                    @Override
                    public boolean configured() {
                        return true;
                    }

                    @Override
                    public StorageProviderConfigSnapshot.Credentials resolve() {
                        return new StorageProviderConfigSnapshot.Credentials(
                                crypto.decrypt("cipher-id"), crypto.decrypt("cipher-secret"));
                    }
                });
        OssStorageProvider provider = new OssStorageProvider(profile, credentials -> {
            assertEquals("access-id", credentials.accessKeyId());
            assertEquals("access-secret", credentials.accessKeySecret());
            clientCreations.incrementAndGet();
            return client;
        }, true);

        verifyNoInteractions(crypto);
        provider.exists("asset.txt");

        assertEquals(1, clientCreations.get());
        verify(crypto).decrypt("cipher-id");
        verify(crypto).decrypt("cipher-secret");
    }

    private static StorageProviderConfig cloudConfig() {
        return StorageProviderConfig.builder()
                .id(7L)
                .configName("oss-profile")
                .provider("oss")
                .endpoint("https://oss.example.test")
                .bucket("blog-assets")
                .region("cn-shanghai")
                .publicUrl("https://cdn.example.test")
                .accessKeyIdCiphertext("cipher-id")
                .accessKeySecretCiphertext("cipher-secret")
                .build();
    }
}
