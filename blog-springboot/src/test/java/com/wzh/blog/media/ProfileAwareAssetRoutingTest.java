package com.wzh.blog.media;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.content.ArticleContentServiceImpl;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.content.ContentAssetPersistenceService;
import com.wzh.blog.content.ContentAssetStore;
import com.wzh.blog.content.MarkdownSanitizer;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.infrastructure.storage.StorageProviderFactory;
import com.wzh.blog.security.UploadValidationService;
import com.wzh.blog.strategy.context.UploadStrategyContext;
import com.wzh.blog.web.CursorCodec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileAwareAssetRoutingTest {

    @Test
    void historicalAssetUsesItsOriginalProfileAfterActiveSwitch() throws Exception {
        StorageProvider oldProvider = mock(StorageProvider.class);
        StorageProvider newProvider = mock(StorageProvider.class);
        when(oldProvider.type()).thenReturn(StorageProviderType.COS);
        when(newProvider.type()).thenReturn(StorageProviderType.COS);
        when(oldProvider.get("articles/1/1-old.md"))
                .thenReturn(storageObject("old content"));

        ProfileRoutingFixture fixture = new ProfileRoutingFixture(oldProvider, newProvider);
        fixture.registry().refresh(12L);

        try (StorageObject object = fixture.openContentAsset(11L, "articles/1/1-old.md")) {
            assertEquals("old content", new String(object.content().readAllBytes(), StandardCharsets.UTF_8));
        }
        verify(oldProvider).get("articles/1/1-old.md");
        verify(newProvider, never()).get("articles/1/1-old.md");
    }

    @Test
    void mediaDeleteAndNewUploadUseRecordedProfileAfterActiveSwitch() throws Exception {
        StorageProvider oldProvider = mock(StorageProvider.class);
        StorageProvider newProvider = mock(StorageProvider.class);
        when(oldProvider.type()).thenReturn(StorageProviderType.COS);
        when(newProvider.type()).thenReturn(StorageProviderType.COS);
        when(oldProvider.put(anyString(), any(), anyLong(), eq("image/png")))
                .thenAnswer(invocation -> new StorageObjectMetadata(
                        invocation.getArgument(0), "image/png", 3L, "old-checksum", Instant.now()));
        when(newProvider.put(anyString(), any(), anyLong(), eq("image/png")))
                .thenAnswer(invocation -> new StorageObjectMetadata(
                        invocation.getArgument(0), "image/png", 3L, "new-checksum", Instant.now()));

        ProfileRoutingFixture fixture = new ProfileRoutingFixture(oldProvider, newProvider);
        MockMultipartFile firstUpload = new MockMultipartFile("file", "old.png", "image/png", new byte[]{1, 2, 3});

        String oldReference = fixture.uploadContext().upload(firstUpload, "articles/");
        MediaAssetLedger.MediaAssetLocation oldLocation = fixture.ledger().locationFor(oldReference);
        assertNotNull(oldLocation);
        assertEquals(11L, oldLocation.storageConfigId());
        assertEquals("cos", oldLocation.provider());
        assertTrue(oldReference.startsWith("https://old.example.test/assets/"));

        fixture.registry().refresh(12L);
        fixture.uploadContext().delete(oldReference);

        verify(oldProvider).delete(oldLocation.objectKey());
        verify(newProvider, never()).delete(oldLocation.objectKey());

        MockMultipartFile secondUpload = new MockMultipartFile("file", "new.png", "image/png", new byte[]{4, 5, 6});
        String newReference = fixture.uploadContext().upload(secondUpload, "articles/");
        MediaAssetLedger.MediaAssetLocation newLocation = fixture.ledger().locationFor(newReference);
        assertNotNull(newLocation);
        assertEquals(12L, newLocation.storageConfigId());
        assertEquals("cos", newLocation.provider());
        assertTrue(newReference.startsWith("https://new.example.test/assets/"));
    }

    @Test
    void legacyObjectKeyReconstructionUsesRecordedProfileBaseAfterActiveSwitch() throws Exception {
        StorageProvider oldProvider = mock(StorageProvider.class);
        StorageProvider newProvider = mock(StorageProvider.class);
        when(oldProvider.type()).thenReturn(StorageProviderType.COS);
        when(newProvider.type()).thenReturn(StorageProviderType.COS);

        ProfileRoutingFixture fixture = new ProfileRoutingFixture(oldProvider, newProvider);
        String oldReference = "https://old.example.test/assets/media/2026/09/original.png";
        fixture.ledger().put(oldReference, new MediaAssetLedger.MediaAssetLocation(11L, "cos", null));
        fixture.registry().refresh(12L);

        fixture.uploadContext().delete(oldReference);

        verify(oldProvider).delete("media/2026/09/original.png");
        verify(newProvider, never()).delete("media/2026/09/original.png");
    }

    @Test
    void retainedMediaKeepsOriginalProfileAndObjectKeyAfterActiveSwitch() throws Exception {
        StorageProvider oldProvider = mock(StorageProvider.class);
        StorageProvider newProvider = mock(StorageProvider.class);
        when(oldProvider.type()).thenReturn(StorageProviderType.COS);
        when(newProvider.type()).thenReturn(StorageProviderType.COS);
        when(oldProvider.put(anyString(), any(), anyLong(), eq("image/png")))
                .thenAnswer(invocation -> new StorageObjectMetadata(
                        invocation.getArgument(0), "image/png", 3L, "old-checksum", Instant.now()));

        ProfileRoutingFixture fixture = new ProfileRoutingFixture(oldProvider, newProvider);
        MockMultipartFile firstUpload = new MockMultipartFile("file", "shared.png", "image/png", new byte[]{1, 2, 3});
        String reference = fixture.uploadContext().upload(firstUpload, "articles/");
        MediaAssetLedger.MediaAssetLocation originalLocation = fixture.ledger().locationFor(reference);
        assertNotNull(originalLocation);

        MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
        when(referenceChecker.isReferenced(reference)).thenReturn(true, false);
        AssetLifecycleService lifecycleService =
                new AssetLifecycleService(fixture.uploadContext(), referenceChecker, fixture.ledger());

        lifecycleService.deleteAfterCommit(java.util.List.of(reference));
        MediaAssetLedger.MediaAssetLocation retainedLocation = fixture.ledger().locationFor(reference);
        assertNotNull(retainedLocation);
        assertEquals(originalLocation.storageConfigId(), retainedLocation.storageConfigId());
        assertEquals(originalLocation.provider(), retainedLocation.provider());
        assertEquals(originalLocation.objectKey(), retainedLocation.objectKey());
        verify(oldProvider, never()).delete(originalLocation.objectKey());

        fixture.registry().refresh(12L);
        lifecycleService.deleteAfterCommit(java.util.List.of(reference));

        verify(oldProvider).delete(originalLocation.objectKey());
        verify(newProvider, never()).delete(originalLocation.objectKey());
    }

    private static StorageObject storageObject(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new StorageObject(
                new ByteArrayInputStream(bytes),
                new StorageObjectMetadata("articles/1/1-old.md",
                        "text/plain", bytes.length, "checksum", Instant.now()));
    }

    private static final class ProfileRoutingFixture {
        private final StorageProviderRegistry registry;
        private final RecordingLedger ledger = new RecordingLedger();
        private final UploadStrategyContext uploadContext;
        private final ArticleContentServiceImpl articleContentService;

        private ProfileRoutingFixture(StorageProvider oldProvider, StorageProvider newProvider) {
            StorageProviderConfig oldConfig = config(11L, "https://old.example.test/assets");
            StorageProviderConfig newConfig = config(12L, "https://new.example.test/assets");
            StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
            when(configDao.selectActive()).thenReturn(oldConfig);
            when(configDao.selectById(11L)).thenReturn(oldConfig);
            when(configDao.selectById(12L)).thenReturn(newConfig);
            this.registry = new StorageProviderRegistry(
                    configDao, new FakeProviderFactory(Map.of(11L, oldProvider, 12L, newProvider)));
            UploadValidationService validation = mock(UploadValidationService.class);
            when(validation.extension(any())).thenReturn(".png");
            this.uploadContext = new UploadStrategyContext(registry, ledger, validation);
            this.articleContentService = new ArticleContentServiceImpl(
                    new MarkdownSanitizer(),
                    registry,
                    mock(ContentAssetPersistenceService.class),
                    mock(ContentAssetStore.class),
                    new CursorCodec("test-secret", Duration.ofMinutes(5)));
        }

        private StorageProviderRegistry registry() {
            return registry;
        }

        private RecordingLedger ledger() {
            return ledger;
        }

        private UploadStrategyContext uploadContext() {
            return uploadContext;
        }

        private StorageObject openContentAsset(Long storageConfigId, String objectKey) {
            return articleContentService.open(ContentAsset.builder()
                    .assetId("asset-1")
                    .articleId(1)
                    .provider("cos")
                    .storageConfigId(storageConfigId)
                    .objectKey(objectKey)
                    .contentType("text/markdown; charset=utf-8")
                    .format("markdown")
                    .version(1)
                    .status("ACTIVE")
                    .build());
        }
    }

    private static final class RecordingLedger implements MediaAssetLedger {
        private final Map<String, MediaAssetLocation> locations = new HashMap<>();

        @Override
        public void register(String reference, String objectKey, String provider, Long storageConfigId) {
            locations.put(reference, new MediaAssetLocation(storageConfigId, provider, objectKey));
        }

        @Override
        public void retain(String reference) {
        }

        @Override
        public MediaAssetLocation locationFor(String reference) {
            return locations.get(reference);
        }

        private void put(String reference, MediaAssetLocation location) {
            locations.put(reference, location);
        }

        @Override
        public void markDeletionStarted(String reference) {
        }

        @Override
        public void markDeleted(String reference) {
        }

        @Override
        public void markDeletionFailed(String reference, String reason) {
        }
    }

    private static StorageProviderConfig config(Long id, String publicUrl) {
        return StorageProviderConfig.builder()
                .id(id)
                .configName("profile-" + id)
                .provider("cos")
                .endpoint("https://cos.example.test")
                .bucket("blog-assets")
                .region("ap-shanghai")
                .publicUrl(publicUrl)
                .accessKeyIdCiphertext("encrypted-id")
                .accessKeySecretCiphertext("encrypted-secret")
                .active(id == 11L)
                .build();
    }

    private static final class FakeProviderFactory extends StorageProviderFactory {
        private final Map<Long, StorageProvider> providers;

        private FakeProviderFactory(Map<Long, StorageProvider> providers) {
            super(new StorageConfigCrypto(""));
            this.providers = providers;
        }

        @Override
        public StorageProvider create(StorageProviderConfig config) {
            return providers.get(config.getId());
        }
    }
}
