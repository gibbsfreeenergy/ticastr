package com.wzh.blog.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StorageProviderRegistryTest {

    @Test
    void normalizesSupportedProviderNamesAndRejectsRemovedProvider() {
        assertEquals(StorageProviderType.LOCAL, StorageProviderType.from(" local "));
        assertEquals(StorageProviderType.OSS, StorageProviderType.from("OSS"));
        assertEquals(StorageProviderType.COS, StorageProviderType.from("cos"));
        assertEquals(StorageProviderType.TOS, StorageProviderType.from("tos"));

        assertThrows(IllegalArgumentException.class, () -> StorageProviderType.from("obs"));
        assertThrows(IllegalArgumentException.class, () -> StorageProviderType.from(""));
        assertThrows(IllegalArgumentException.class, () -> StorageProviderType.from(null));
    }

    @Test
    void selectsActiveProviderWithoutPerformingNetworkWorkAtConstruction() {
        TestProvider local = new TestProvider(StorageProviderType.LOCAL);
        TestProvider oss = new TestProvider(StorageProviderType.OSS);
        TestProvider cos = new TestProvider(StorageProviderType.COS);
        TestProvider tos = new TestProvider(StorageProviderType.TOS);

        StorageProviderRegistry registry = new StorageProviderRegistry(
                StorageProviderType.COS, List.of(local, oss, cos, tos));

        assertSame(cos, registry.providerForNewAsset());
        assertEquals(0, local.operations());
        assertEquals(0, oss.operations());
        assertEquals(0, cos.operations());
        assertEquals(0, tos.operations());

        registry.refresh(StorageProviderType.TOS);
        assertSame(tos, registry.providerForNewAsset());
        assertSame(cos, registry.providerFor(StorageProviderType.COS));
    }

    @Test
    void rejectsUnsafeObjectKeysBeforeProviderCall() {
        TestProvider provider = new TestProvider(StorageProviderType.LOCAL);

        assertThrows(IllegalArgumentException.class,
                () -> provider.put("../secret", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.put("/absolute", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.put("media\\windows", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain"));
        assertEquals(0, provider.operations());
    }

    private static final class TestProvider implements StorageProvider {
        private final StorageProviderType type;
        private final AtomicInteger operations = new AtomicInteger();

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
            ObjectKeyPolicy.requireSafe(objectKey);
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

        private int operations() {
            return operations.get();
        }
    }
}
