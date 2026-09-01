package com.wzh.blog.config;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
