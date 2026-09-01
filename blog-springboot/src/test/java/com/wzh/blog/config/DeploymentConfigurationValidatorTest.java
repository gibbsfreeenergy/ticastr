package com.wzh.blog.config;

import com.wzh.blog.bootstrap.StorageConfigBootstrapRunner;
import com.wzh.blog.dao.StorageBootstrapStateDao;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageBootstrapState;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentConfigurationValidatorTest {

    @Test
    void permitsProductionLikeDeploymentWithDatabaseSelectedLocalProfileAndNoEncryptionKey() {
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderConfig local = localProfile(1L);
        when(configDao.selectActive()).thenReturn(local);
        when(configDao.selectAll()).thenReturn(List.of(local));
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.hasKey()).thenReturn(false);

        assertThatCode(() -> validator(configDao, crypto).validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsCloudCatalogWithoutAValidEncryptionKey() {
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderConfig cloud = cloudProfile(2L);
        when(configDao.selectActive()).thenReturn(cloud);
        when(configDao.selectAll()).thenReturn(List.of(cloud));
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.hasKey()).thenReturn(false);

        assertThatThrownBy(() -> validator(configDao, crypto).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Storage configuration encryption is required for cloud profiles");
    }

    @Test
    void rejectsAnIncompleteCloudDraftWithoutAnEncryptionKey() {
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderConfig local = localProfile(2L);
        StorageProviderConfig incompleteCloud = StorageProviderConfig.builder()
                .id(3L).configName("draft").provider("oss").build();
        when(configDao.selectActive()).thenReturn(local);
        when(configDao.selectAll()).thenReturn(List.of(local, incompleteCloud));
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.hasKey()).thenReturn(false);

        assertThatThrownBy(() -> validator(configDao, crypto).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Storage configuration encryption is required for cloud profiles");
    }

    @Test
    void legacyEnvironmentActiveProviderCannotOverrideDatabaseSelectedProfile() {
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderConfig local = localProfile(3L);
        when(configDao.selectActive()).thenReturn(local);
        when(configDao.selectAll()).thenReturn(List.of(local));
        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.hasKey()).thenReturn(false);
        System.setProperty("STORAGE_ACTIVE_PROVIDER", "oss");
        try {
            assertThatCode(() -> validator(configDao, crypto).validate())
                    .doesNotThrowAnyException();
        } finally {
            System.clearProperty("STORAGE_ACTIVE_PROVIDER");
        }
    }

    @Test
    void rejectsRelativeLocalOrPlaceholderPublicUrlsForProductionLikeProfiles() {
        for (String publicUrl : List.of("/uploads/", "http://localhost:8090/uploads/",
                "https://cdn.example.com/uploads/")) {
            StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
            StorageProviderConfig local = localProfile(4L);
            local.setPublicUrl(publicUrl);
            when(configDao.selectActive()).thenReturn(local);
            when(configDao.selectAll()).thenReturn(List.of(local));

            assertThatThrownBy(() -> validator(configDao, mock(StorageConfigCrypto.class)).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active local storage public URL");
        }
    }

    @Test
    void validatesTheDatabaseProfileOnlyAfterBootstrapRunnerCompletes() throws Exception {
        assertThatCode(() -> {
            if (StorageConfigBootstrapRunner.class.getAnnotation(Order.class).value()
                    >= DeploymentConfigurationValidator.class.getAnnotation(Order.class).value()) {
                throw new AssertionError("bootstrap must run before deployment validation");
            }
        }).doesNotThrowAnyException();
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderConfig seededDefault = localProfile(1L);
        seededDefault.setConfigSource("DEFAULT");
        seededDefault.setPublicUrl("/uploads/");
        List<StorageProviderConfig> catalog = new ArrayList<>(List.of(seededDefault));
        AtomicReference<StorageProviderConfig> active = new AtomicReference<>(seededDefault);
        when(stateDao.selectForUpdate()).thenReturn(StorageBootstrapState.builder()
                .id(1).legacyImportCompleted(false).legacyActiveProvider("local").build());
        when(configDao.upsertLegacyProfile(any())).thenAnswer(invocation -> {
            StorageProviderConfig imported = invocation.getArgument(0);
            imported.setId(10L);
            catalog.add(imported);
            return 1;
        });
        when(configDao.selectAll()).thenAnswer(invocation -> List.copyOf(catalog));
        when(configDao.selectActive()).thenAnswer(invocation -> active.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            active.set(catalog.stream().filter(profile -> id.equals(profile.getId())).findFirst().orElseThrow());
            return 1;
        }).when(configDao).activateOnly(anyLong(), any(), any(LocalDateTime.class));

        StorageConfigCrypto crypto = mock(StorageConfigCrypto.class);
        when(crypto.hasKey()).thenReturn(false);
        DeploymentConfigurationValidator validator = validator(configDao, crypto);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active local storage public URL");

        StorageProperties legacy = new StorageProperties();
        legacy.setLocalRoot("C:/legacy/uploads");
        legacy.setLocalPublicUrl("https://cdn.ticastr.test/uploads/");
        new StorageConfigBootstrapRunner(stateDao, configDao,
                new StorageConfigCrypto(Base64.getEncoder().encodeToString(new byte[32])), legacy,
                mock(JdbcTemplate.class), mock(StorageProviderRegistry.class))
                .run(mock(ApplicationArguments.class));

        assertThatCode(() -> validator.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }

    private DeploymentConfigurationValidator validator(StorageProviderConfigDao configDao, StorageConfigCrypto crypto) {
        return new DeploymentConfigurationValidator(
                "production-like",
                "https://blog.ticastr.test",
                "https://api.ticastr.test",
                "https://blog.ticastr.test",
                "search-index",
                "monitoring-token",
                "a-strong-cursor-secret-with-more-than-32-characters",
                configDao,
                crypto);
    }

    private StorageProviderConfig localProfile(Long id) {
        return StorageProviderConfig.builder().id(id).provider("local").configName("local")
                .localRoot("/data/uploads").publicUrl("https://cdn.ticastr.test/uploads/").active(true).build();
    }

    private StorageProviderConfig cloudProfile(Long id) {
        return StorageProviderConfig.builder().id(id).provider("oss").configName("cloud")
                .endpoint("https://oss.ticastr.test").region("region-1").bucket("bucket")
                .publicUrl("https://cdn.ticastr.test/").accessKeyIdCiphertext("v1:key")
                .accessKeySecretCiphertext("v1:secret").active(true).build();
    }
}
