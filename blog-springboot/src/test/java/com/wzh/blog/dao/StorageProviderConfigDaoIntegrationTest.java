package com.wzh.blog.dao;

import com.wzh.blog.BlogApplication;
import com.wzh.blog.entity.StorageProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = BlogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class StorageProviderConfigDaoIntegrationTest {

    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.4")
            .withEnv("MYSQL_ROOT_PASSWORD", "test-root")
            .withEnv("MYSQL_DATABASE", "mapper_blog")
            .withExposedPorts(3306);

    @Autowired
    private StorageProviderConfigDao configDao;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + MYSQL.getHost()
                + ":" + MYSQL.getMappedPort(3306)
                + "/mapper_blog?allowMultiQueries=true&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "test-root");
    }

    @Test
    void rejectsUnknownProviderAndPreservesExistingActiveProfile() {
        assertThatThrownBy(() -> configDao.insertProfile(profile("bad-provider", "gcs", "ADMIN", false)))
                .hasMessageContaining("provider");

        assertThat(configDao.selectAll()).hasSize(1);
        assertThat(configDao.selectActive())
                .extracting(StorageProviderConfig::getId,
                        StorageProviderConfig::getProvider,
                        StorageProviderConfig::getActive)
                .containsExactly(1L, "local", true);
    }

    @Test
    void mapsActiveColumnIntoActivePropertyAcrossSelectors() {
        StorageProviderConfig active = configDao.selectActive();
        StorageProviderConfig singleton = configDao.selectSingleton();
        StorageProviderConfig byId = configDao.selectById(1L);
        StorageProviderConfig byIdForUpdate = configDao.selectByIdForUpdate(1L);

        assertThat(active).isNotNull();
        assertThat(active.getProvider()).isEqualTo("local");
        assertThat(active.getActive()).isTrue();
        assertThat(singleton).isNotNull();
        assertThat(singleton.getActive()).isTrue();
        assertThat(byId).isNotNull();
        assertThat(byId.getActive()).isTrue();
        assertThat(byIdForUpdate).isNotNull();
        assertThat(byIdForUpdate.getActive()).isTrue();
        assertThat(configDao.selectAll())
                .extracting(StorageProviderConfig::getActive)
                .contains(Boolean.TRUE);
    }

    @Test
    void legacyUpsertUsesLegacyConflictTargetWithoutTouchingActiveOrAdminProfiles() {
        StorageProviderConfig adminOne = profile("admin-cos-1", "cos", "ADMIN", false);
        StorageProviderConfig adminTwo = profile("admin-cos-2", "cos", "ADMIN", false);

        assertThat(configDao.insertProfile(adminOne)).isEqualTo(1);
        assertThat(configDao.insertProfile(adminTwo)).isEqualTo(1);

        StorageProviderConfig legacyCos = profile("legacy-cos", "cos", "LEGACY_ENV", true);

        int inserted = configDao.upsertLegacyProfile(legacyCos);

        assertThat(inserted).isEqualTo(1);
        assertThat(legacyCos.getId()).isNotNull();
        assertThat(legacyCos.getId()).isNotEqualTo(1L);
        StorageProviderConfig imported = configDao.selectById(legacyCos.getId());
        assertThat(imported.getProvider()).isEqualTo("cos");
        assertThat(imported.getConfigSource()).isEqualTo("LEGACY_ENV");
        assertThat(imported.getActive()).isFalse();
        StorageProviderConfig defaultProfile = configDao.selectById(1L);
        assertThat(defaultProfile.getProvider()).isEqualTo("local");
        assertThat(defaultProfile.getConfigName()).isEqualTo("本地默认配置");
        assertThat(defaultProfile.getActive()).isTrue();
        assertThat(configDao.countByProvider("cos")).isEqualTo(3);

        StorageProviderConfig updatedLegacyCos = profile("legacy-cos-updated", "cos", "LEGACY_ENV", true);
        updatedLegacyCos.setEndpoint("https://updated-storage.example.com");

        int updated = configDao.upsertLegacyProfile(updatedLegacyCos);

        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(updatedLegacyCos.getId()).isEqualTo(legacyCos.getId());
        assertThat(configDao.countByProvider("cos")).isEqualTo(3);
        assertThat(configDao.selectById(legacyCos.getId()))
                .extracting(StorageProviderConfig::getConfigName,
                        StorageProviderConfig::getEndpoint,
                        StorageProviderConfig::getConfigSource,
                        StorageProviderConfig::getActive)
                .containsExactly("legacy-cos-updated",
                        "https://updated-storage.example.com",
                        "LEGACY_ENV",
                        false);
        assertThat(configDao.selectAll())
                .filteredOn(StorageProviderConfig::getActive)
                .hasSize(1)
                .extracting(StorageProviderConfig::getId)
                .containsExactly(1L);
    }

    private static StorageProviderConfig profile(String name, String provider, String source, boolean active) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 0, 0);
        return StorageProviderConfig.builder()
                .configName(name)
                .provider(provider)
                .endpoint("https://storage.example.com")
                .bucket("blog")
                .region("ap-shanghai")
                .localRoot("uploads")
                .publicUrl("/uploads/")
                .accessKeyIdCiphertext("encrypted-id")
                .accessKeySecretCiphertext("encrypted-secret")
                .active(active)
                .configSource(source)
                .lastValidationStatus("NEVER")
                .usageStatus("NEVER")
                .createdAt(now)
                .updatedAt(now)
                .updatedBy(null)
                .build();
    }
}
