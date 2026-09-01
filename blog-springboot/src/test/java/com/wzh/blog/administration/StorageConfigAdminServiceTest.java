package com.wzh.blog.administration;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.exception.ConflictException;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.media.StorageUsage;
import com.wzh.blog.vo.StorageConfigRequest;
import com.wzh.blog.vo.StorageConfigSummaryVO;
import com.wzh.blog.vo.StorageUsageVO;
import com.wzh.blog.vo.StorageValidationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageConfigAdminServiceTest {

    private StorageProviderConfigDao configDao;
    private StorageProviderRegistry registry;
    private StorageConfigCrypto crypto;
    private StorageConfigAdminService service;

    @BeforeEach
    void setUp() {
        configDao = mock(StorageProviderConfigDao.class);
        registry = mock(StorageProviderRegistry.class);
        crypto = mock(StorageConfigCrypto.class);
        service = new StorageConfigAdminService(configDao, registry, crypto);
    }

    @Test
    void createRejectsIncompleteCloudAndUnsafeUrls() {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                cloudRequest("https://key:secret@storage.example.com", ""), 7));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                cloudRequest("https://storage.example.com?token=secret", "secret"), 7));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new StorageConfigRequest("local", "local", null, null, null, "relative/root",
                        "https://cdn.example.com", null, null), 7));
        verify(registry, never()).providerForConfig(anyLong());
    }

    @Test
    void createEncryptsCloudCredentialsWithoutConstructingProvider() {
        when(crypto.encrypt("key")).thenReturn("v1:key-ciphertext");
        when(crypto.encrypt("secret")).thenReturn("v1:secret-ciphertext");
        when(configDao.insertProfile(any())).thenAnswer(invocation -> {
            invocation.<StorageProviderConfig>getArgument(0).setId(11L);
            return 1;
        });

        StorageConfigSummaryVO result = service.create(cloudRequest("https://storage.example.com", "secret"), 7);

        assertEquals(11L, result.id());
        assertTrue(result.credentialsConfigured());
        ArgumentCaptor<StorageProviderConfig> captured = ArgumentCaptor.forClass(StorageProviderConfig.class);
        verify(configDao).insertProfile(captured.capture());
        assertEquals("v1:key-ciphertext", captured.getValue().getAccessKeyIdCiphertext());
        assertEquals("v1:secret-ciphertext", captured.getValue().getAccessKeySecretCiphertext());
        verify(registry, never()).providerForConfig(anyLong());
    }

    @Test
    void updateBlankCredentialsPreservesExistingCiphertext() {
        StorageProviderConfig existing = cloudConfig(12L, false);
        existing.setAccessKeyIdCiphertext("v1:existing-id");
        existing.setAccessKeySecretCiphertext("v1:existing-secret");
        when(configDao.selectById(12L)).thenReturn(existing);
        when(configDao.updateProfile(any())).thenReturn(1);

        service.update(12L, new StorageConfigRequest("renamed", "oss", "https://storage.example.com",
                "cn-hangzhou", "bucket", null, "https://cdn.example.com", " ", ""), 9);

        ArgumentCaptor<StorageProviderConfig> captured = ArgumentCaptor.forClass(StorageProviderConfig.class);
        verify(configDao).updateProfile(captured.capture());
        assertEquals("v1:existing-id", captured.getValue().getAccessKeyIdCiphertext());
        assertEquals("v1:existing-secret", captured.getValue().getAccessKeySecretCiphertext());
        verify(crypto, never()).encrypt(anyString());
        verify(registry).invalidate(12L);
    }

    @Test
    void validationPersistsSafeSuccessAndFailureMessages() {
        StorageProviderConfig config = localConfig(21L, false);
        StorageProvider provider = healthyProvider();
        when(configDao.selectById(21L)).thenReturn(config);
        when(registry.providerForConfig(21L)).thenReturn(provider);

        StorageValidationVO success = service.validate(21L);

        assertTrue(success.success());
        assertEquals("验证成功", success.message());
        verify(configDao).updateValidation(anyLong(), org.mockito.ArgumentMatchers.eq("SUCCESS"), any(),
                org.mockito.ArgumentMatchers.eq("验证成功"));

        doThrow(new RuntimeException("secret=not-for-response https://signed.example.com/path"))
                .when(provider).validateConnection();
        StorageValidationVO failure = service.validate(21L);

        assertFalse(failure.success());
        assertEquals("配置验证失败", failure.message());
        assertFalse(failure.message().contains("secret"));
        verify(configDao).updateValidation(anyLong(), org.mockito.ArgumentMatchers.eq("FAILED"), any(),
                org.mockito.ArgumentMatchers.eq("配置验证失败"));
    }

    @Test
    void failedActivationLeavesCurrentProfileAndSuccessfulActivationRefreshesRegistry() {
        StorageProviderConfig target = localConfig(31L, false);
        StorageProviderConfig active = localConfig(30L, true);
        StorageProvider provider = healthyProvider();
        when(configDao.selectById(31L)).thenReturn(target);
        when(configDao.selectByIdForUpdate(31L)).thenReturn(target);
        when(configDao.selectActive()).thenReturn(active);
        when(registry.providerForConfig(31L)).thenReturn(provider);

        doThrow(new RuntimeException("authorization: leaked")).when(provider).validateConnection();
        assertThrows(ConflictException.class, () -> service.activate(31L, 8));
        verify(configDao, never()).activateOnly(anyLong(), any(), any());
        verify(registry, never()).refresh(31L);

        StorageProvider healthy = healthyProvider();
        when(registry.providerForConfig(31L)).thenReturn(healthy);
        StorageConfigSummaryVO activated = service.activate(31L, 8);

        assertTrue(activated.active());
        verify(configDao).activateOnly(org.mockito.ArgumentMatchers.eq(31L), org.mockito.ArgumentMatchers.eq(8), any());
        verify(registry).refresh(31L);
    }

    @Test
    void usageSuccessPersistsSnapshotAndFailureRetainsLastSuccessfulValues() throws Exception {
        StorageProviderConfig config = localConfig(41L, false);
        config.setUsageObjectCount(4L);
        config.setUsageBytes(400L);
        config.setUsageLastModified(LocalDateTime.of(2026, 8, 1, 1, 2));
        StorageProvider provider = mock(StorageProvider.class);
        when(configDao.selectById(41L)).thenReturn(config);
        when(registry.providerForConfig(41L)).thenReturn(provider);
        when(provider.usage()).thenReturn(new StorageUsage(9, 900, Instant.parse("2026-08-31T08:20:00Z")));

        StorageUsageVO success = service.refreshUsage(41L);

        assertEquals("SUCCESS", success.status());
        assertEquals(9L, success.objectCount());
        verify(configDao).updateUsage(org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(900L), any(), any(),
                org.mockito.ArgumentMatchers.isNull());

        when(provider.usage()).thenThrow(new java.io.IOException("Authorization=signed-secret"));
        StorageUsageVO failed = service.refreshUsage(41L);

        assertEquals("FAILED", failed.status());
        assertEquals(4L, failed.objectCount());
        assertEquals(400L, failed.bytes());
        assertEquals("使用量刷新失败", failed.error());
        verify(configDao).updateUsage(org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.eq(400L), any(), any(),
                org.mockito.ArgumentMatchers.eq("使用量刷新失败"));
    }

    @Test
    void deletionRejectsActiveAndReferencedProfiles() {
        StorageProviderConfig active = localConfig(51L, true);
        StorageProviderConfig referenced = localConfig(52L, false);
        when(configDao.selectByIdForUpdate(51L)).thenReturn(active);
        when(configDao.selectByIdForUpdate(52L)).thenReturn(referenced);
        when(configDao.selectActiveForUpdate()).thenReturn(active);
        when(configDao.countAssetReferences(52L)).thenReturn(1);

        assertThrows(ConflictException.class, () -> service.delete(51L));
        assertThrows(ConflictException.class, () -> service.delete(52L));
        verify(configDao, never()).deleteById(anyLong());
    }

    @Test
    void deletionLocksTargetAndActiveBeforeCheckingReferencesAndDeleting() {
        StorageProviderConfig target = localConfig(53L, false);
        StorageProviderConfig active = localConfig(54L, true);
        when(configDao.selectByIdForUpdate(53L)).thenReturn(target);
        when(configDao.selectActiveForUpdate()).thenReturn(active);
        when(configDao.countAssetReferences(53L)).thenReturn(0);
        when(configDao.deleteById(53L)).thenReturn(1);

        service.delete(53L);

        InOrder order = org.mockito.Mockito.inOrder(configDao);
        order.verify(configDao).selectByIdForUpdate(53L);
        order.verify(configDao).selectActiveForUpdate();
        order.verify(configDao).countAssetReferences(53L);
        order.verify(configDao).deleteById(53L);
        verify(registry).invalidate(53L);
    }

    @Test
    void compatibilitySwitchRejectsAmbiguousProfiles() {
        when(configDao.selectAll()).thenReturn(List.of(cloudConfig(61L, false), cloudConfig(62L, false)));

        assertThrows(ConflictException.class, () -> service.switchProvider("oss", 3));
        verify(configDao, never()).activateOnly(anyLong(), any(), any());
    }

    @Test
    void compatibilityProviderOperationsRejectConfiguredProfileAlongsideDraft() {
        StorageProviderConfig draft = StorageProviderConfig.builder().id(63L).configName("draft").provider("oss")
                .publicUrl("https://cdn.example.com").lastValidationStatus("NEVER").usageStatus("NEVER").build();
        when(configDao.selectAll()).thenReturn(List.of(cloudConfig(61L, false), draft));

        assertThrows(ConflictException.class, () -> service.validateProvider("oss"));
        assertThrows(ConflictException.class, () -> service.switchProvider("oss", 3));
        verify(registry, never()).providerForConfig(anyLong());
        verify(configDao, never()).activateOnly(anyLong(), any(), any());
    }

    @Test
    void providerFailureDiagnosticsAndResponsesNeverContainSensitiveExceptionText() {
        String sensitive = "Authorization=Bearer secret-token signature=abc v1:ciphertext https://provider.example.com/path";

        String diagnostic = StorageConfigAdminService.safeProviderFailureDiagnostic(71L,
                new IllegalStateException(sensitive));

        assertTrue(diagnostic.contains("configId=71"));
        assertTrue(diagnostic.contains("exceptionType=IllegalStateException"));
        assertFalse(diagnostic.contains("secret-token"));
        assertFalse(diagnostic.contains("signature"));
        assertFalse(diagnostic.contains("ciphertext"));
        assertFalse(diagnostic.contains("https://provider.example.com"));
    }

    private StorageConfigRequest cloudRequest(String endpoint, String secret) {
        return new StorageConfigRequest("cloud", "oss", endpoint, "cn-hangzhou", "bucket", null,
                "https://cdn.example.com", "key", secret);
    }

    private StorageProviderConfig localConfig(Long id, boolean active) {
        return StorageProviderConfig.builder().id(id).configName("local").provider("local")
                .localRoot("C:/storage").publicUrl("https://cdn.example.com").active(active)
                .lastValidationStatus("NEVER").usageStatus("NEVER").build();
    }

    private StorageProviderConfig cloudConfig(Long id, boolean active) {
        return StorageProviderConfig.builder().id(id).configName("cloud").provider("oss")
                .endpoint("https://storage.example.com").region("cn-hangzhou").bucket("bucket")
                .publicUrl("https://cdn.example.com").accessKeyIdCiphertext("v1:key")
                .accessKeySecretCiphertext("v1:secret").active(active).lastValidationStatus("NEVER")
                .usageStatus("NEVER").build();
    }

    private StorageProvider healthyProvider() {
        StorageProvider provider = mock(StorageProvider.class);
        StorageObjectMetadata metadata = new StorageObjectMetadata(".health/admin/test.txt", "text/plain", 22,
                "checksum", Instant.now());
        try {
            when(provider.type()).thenReturn(StorageProviderType.LOCAL);
            when(provider.put(anyString(), any(), org.mockito.ArgumentMatchers.eq(22L), anyString())).thenReturn(metadata);
            when(provider.head(anyString())).thenReturn(metadata);
            when(provider.get(anyString())).thenReturn(new StorageObject(
                    new ByteArrayInputStream("ticastr-storage-health".getBytes()), metadata));
            when(provider.exists(anyString())).thenReturn(false);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        return provider;
    }
}
