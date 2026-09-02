package com.wzh.blog.media;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.infrastructure.storage.StorageProviderFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProviderRegistryTest {

    @Test
    void routesNewAndHistoricalObjectsByConfigIdWithoutCreatingProvidersAtConstruction() {
        StorageProviderConfig oldConfig = config(11L, "old-cos", true);
        StorageProviderConfig newConfig = config(12L, "new-cos", false);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        when(configDao.selectActive()).thenReturn(oldConfig);
        when(configDao.selectById(11L)).thenReturn(oldConfig);
        when(configDao.selectById(12L)).thenReturn(newConfig);

        TestProvider oldCos = new TestProvider(StorageProviderType.COS);
        TestProvider newCos = new TestProvider(StorageProviderType.COS);
        FakeProviderFactory factory = new FakeProviderFactory(Map.of(11L, oldCos, 12L, newCos));

        StorageProviderRegistry registry = new StorageProviderRegistry(configDao, factory);

        assertEquals(0, factory.created());
        assertSame(oldCos, registry.providerForNewAsset());
        registry.refresh(12L);
        assertSame(newCos, registry.providerForNewAsset());
        assertSame(oldCos, registry.providerForConfig(11L));
        assertEquals(12L, registry.activeConfigId());
        assertEquals(0, oldCos.operations());
        assertEquals(0, newCos.operations());
    }

    @Test
    void closesEveryCachedProviderAndIsIdempotent() {
        StorageProviderConfig oldConfig = config(21L, "old-cos", true);
        StorageProviderConfig newConfig = config(22L, "new-cos", false);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        when(configDao.selectActive()).thenReturn(oldConfig);
        when(configDao.selectById(21L)).thenReturn(oldConfig);
        when(configDao.selectById(22L)).thenReturn(newConfig);

        TestProvider oldCos = new TestProvider(StorageProviderType.COS);
        TestProvider newCos = new TestProvider(StorageProviderType.COS);
        FakeProviderFactory factory = new FakeProviderFactory(Map.of(21L, oldCos, 22L, newCos));
        StorageProviderRegistry registry = new StorageProviderRegistry(configDao, factory);

        assertSame(oldCos, registry.providerForConfig(21L));
        assertSame(newCos, registry.providerForConfig(22L));

        registry.closeAll();
        registry.close();

        assertEquals(1, oldCos.closeCount());
        assertEquals(1, newCos.closeCount());
        assertThrows(IllegalStateException.class, () -> registry.providerForConfig(21L));
        assertThrows(IllegalStateException.class, registry::activeConfig);
    }

    private static StorageProviderConfig config(long id, String name, boolean active) {
        return StorageProviderConfig.builder()
                .id(id)
                .configName(name)
                .provider("cos")
                .endpoint("https://cos.example.test")
                .bucket("blog-assets")
                .region("ap-shanghai")
                .publicUrl("https://cdn.example.test/assets")
                .accessKeyIdCiphertext("encrypted-id")
                .accessKeySecretCiphertext("encrypted-secret")
                .active(active)
                .build();
    }

    private static final class FakeProviderFactory extends StorageProviderFactory {
        private final Map<Long, StorageProvider> providers;
        private final AtomicInteger created = new AtomicInteger();

        private FakeProviderFactory(Map<Long, StorageProvider> providers) {
            super(new StorageConfigCrypto(""));
            this.providers = providers;
        }

        @Override
        public StorageProvider create(StorageProviderConfig config) {
            created.incrementAndGet();
            return providers.get(config.getId());
        }

        private int created() {
            return created.get();
        }
    }

    private static final class TestProvider implements StorageProvider {
        private final StorageProviderType type;
        private final AtomicInteger operations = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        private TestProvider(StorageProviderType type) {
            this.type = type;
        }

        @Override
        public StorageProviderType type() {
            return type;
        }

        @Override
        public StorageObjectMetadata put(String objectKey, java.io.InputStream content, long size, String contentType)
                throws IOException {
            operations.incrementAndGet();
            return new StorageObjectMetadata(objectKey, contentType, size, "checksum", Instant.now());
        }

        @Override
        public StorageObject get(String objectKey) {
            operations.incrementAndGet();
            return null;
        }

        @Override
        public StorageObjectMetadata head(String objectKey) {
            operations.incrementAndGet();
            return null;
        }

        @Override
        public void delete(String objectKey) {
            operations.incrementAndGet();
        }

        @Override
        public boolean exists(String objectKey) {
            operations.incrementAndGet();
            return false;
        }

        @Override
        public void validateConnection() {
            operations.incrementAndGet();
        }

        @Override
        public StorageUsage usage() {
            operations.incrementAndGet();
            return new StorageUsage(0, 0, null);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }

        private int operations() {
            return operations.get();
        }

        private int closeCount() {
            return closes.get();
        }
    }
}
