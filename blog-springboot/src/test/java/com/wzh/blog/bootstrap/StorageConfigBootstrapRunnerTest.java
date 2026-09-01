package com.wzh.blog.bootstrap;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.config.StorageProperties;
import com.wzh.blog.dao.StorageBootstrapStateDao;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageBootstrapState;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProviderRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageConfigBootstrapRunnerTest {

    @Test
    void importsConfiguredLegacyProfilesEncryptsCloudCredentialsAndSelectsOldActiveProvider() throws Exception {
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StorageProviderRegistry registry = mock(StorageProviderRegistry.class);
        StorageProperties properties = configuredProperties();
        StorageConfigCrypto crypto = new StorageConfigCrypto(base64Key());
        List<StorageProviderConfig> profiles = new ArrayList<>(List.of(localDefault(1L)));
        AtomicLong ids = new AtomicLong(10L);
        when(stateDao.selectForUpdate()).thenReturn(state(false, "oss"));
        when(configDao.upsertLegacyProfile(any())).thenAnswer(invocation -> {
            StorageProviderConfig profile = invocation.getArgument(0);
            profile.setId(ids.getAndIncrement());
            profiles.add(profile);
            return 1;
        });
        when(configDao.selectAll()).thenAnswer(invocation -> List.copyOf(profiles));

        runner(stateDao, configDao, crypto, properties, jdbcTemplate, registry)
                .run(mock(ApplicationArguments.class));

        ArgumentCaptor<StorageProviderConfig> imported = ArgumentCaptor.forClass(StorageProviderConfig.class);
        verify(configDao, times(4)).upsertLegacyProfile(imported.capture());
        assertThat(imported.getAllValues()).extracting(StorageProviderConfig::getProvider)
                .containsExactlyInAnyOrder("local", "cos", "oss", "tos");
        StorageProviderConfig oss = imported.getAllValues().stream()
                .filter(profile -> "oss".equals(profile.getProvider()))
                .findFirst().orElseThrow();
        assertThat(oss.getConfigSource()).isEqualTo("LEGACY_ENV");
        assertThat(oss.getAccessKeyIdCiphertext()).doesNotContain("oss-key");
        assertThat(crypto.decrypt(oss.getAccessKeyIdCiphertext())).isEqualTo("oss-key");
        assertThat(crypto.decrypt(oss.getAccessKeySecretCiphertext())).isEqualTo("oss-secret");
        verify(configDao).activateOnly(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.isNull(), any(LocalDateTime.class));
        verify(registry).refresh(12L);
        verify(stateDao).markCompleted(any(LocalDateTime.class));
    }

    @Test
    void fallsBackToSeededLocalWhenOldActiveCloudProfileIsIncomplete() throws Exception {
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProviderRegistry registry = mock(StorageProviderRegistry.class);
        StorageProperties properties = configuredProperties();
        properties.getTos().setAccessKeySecret("");
        when(stateDao.selectForUpdate()).thenReturn(state(false, "tos"));
        when(configDao.selectAll()).thenReturn(List.of(localDefault(1L)));

        runner(stateDao, configDao, new StorageConfigCrypto(base64Key()), properties,
                mock(JdbcTemplate.class), registry).run(mock(ApplicationArguments.class));

        verify(configDao).activateOnly(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.isNull(), any(LocalDateTime.class));
        verify(registry).refresh(1L);
        verify(stateDao).markCompleted(any(LocalDateTime.class));
    }

    @Test
    void doesNotImportAgainWhenTheSecondLockedStateIsCompleted() throws Exception {
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProperties properties = configuredProperties();
        List<StorageProviderConfig> profiles = new ArrayList<>(List.of(localDefault(1L)));
        when(stateDao.selectForUpdate()).thenReturn(state(false, "local"), state(true, "oss"));
        when(configDao.upsertLegacyProfile(any())).thenAnswer(invocation -> {
            StorageProviderConfig profile = invocation.getArgument(0);
            profile.setId((long) profiles.size() + 1);
            profiles.add(profile);
            return 1;
        });
        when(configDao.selectAll()).thenAnswer(invocation -> List.copyOf(profiles));
        StorageConfigBootstrapRunner runner = runner(stateDao, configDao, new StorageConfigCrypto(base64Key()),
                properties, mock(JdbcTemplate.class), mock(StorageProviderRegistry.class));

        runner.run(mock(ApplicationArguments.class));
        runner.run(mock(ApplicationArguments.class));

        verify(configDao, times(4)).upsertLegacyProfile(any());
        verify(stateDao, times(1)).markCompleted(any(LocalDateTime.class));
    }

    @Test
    void completesTheBootstrapWhenNoLegacyCloudConfigurationExists() throws Exception {
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot("");
        properties.setLocalPublicUrl("");
        when(stateDao.selectForUpdate()).thenReturn(state(false, "oss"));
        when(configDao.selectAll()).thenReturn(List.of(localDefault(1L)));

        runner(stateDao, configDao, new StorageConfigCrypto(base64Key()), properties,
                mock(JdbcTemplate.class), mock(StorageProviderRegistry.class))
                .run(mock(ApplicationArguments.class));

        verify(configDao, never()).upsertLegacyProfile(any());
        verify(stateDao).markCompleted(any(LocalDateTime.class));
    }

    @Test
    void backfillsAssetsOnlyForAProviderWithExactlyOneUsableProfile() throws Exception {
        StorageBootstrapStateDao stateDao = mock(StorageBootstrapStateDao.class);
        StorageProviderConfigDao configDao = mock(StorageProviderConfigDao.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot("");
        properties.setLocalPublicUrl("");
        when(stateDao.selectForUpdate()).thenReturn(state(false, "local"));
        when(configDao.selectAll()).thenReturn(List.of(
                localDefault(1L), cloud(2L, "cos"), cloud(3L, "oss"), cloud(4L, "oss")));

        runner(stateDao, configDao, new StorageConfigCrypto(base64Key()), properties, jdbcTemplate,
                mock(StorageProviderRegistry.class)).run(mock(ApplicationArguments.class));

        verify(jdbcTemplate).update(startsWith("UPDATE tb_content_asset"), anyLong(), org.mockito.ArgumentMatchers.eq("cos"));
        verify(jdbcTemplate).update(startsWith("UPDATE tb_media_asset"), anyLong(), org.mockito.ArgumentMatchers.eq("cos"));
        verify(jdbcTemplate, never()).update(startsWith("UPDATE tb_content_asset"), anyLong(), org.mockito.ArgumentMatchers.eq("oss"));
        verify(jdbcTemplate, never()).update(startsWith("UPDATE tb_media_asset"), anyLong(), org.mockito.ArgumentMatchers.eq("oss"));
    }

    private StorageConfigBootstrapRunner runner(StorageBootstrapStateDao stateDao,
                                                 StorageProviderConfigDao configDao,
                                                 StorageConfigCrypto crypto,
                                                 StorageProperties properties,
                                                 JdbcTemplate jdbcTemplate,
                                                 StorageProviderRegistry registry) {
        return new StorageConfigBootstrapRunner(stateDao, configDao, crypto, properties, jdbcTemplate, registry);
    }

    private StorageProperties configuredProperties() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalRoot("C:/legacy/uploads");
        properties.setLocalPublicUrl("https://cdn.example.test/uploads/");
        configure(properties.getCos(), "cos");
        configure(properties.getOss(), "oss");
        configure(properties.getTos(), "tos");
        return properties;
    }

    private void configure(StorageProperties.Provider provider, String name) {
        provider.setEndpoint("https://" + name + ".storage.example.test");
        provider.setRegion("region-1");
        provider.setBucket(name + "-bucket");
        provider.setPublicUrl("https://" + name + ".cdn.example.test/");
        provider.setAccessKeyId(name + "-key");
        provider.setAccessKeySecret(name + "-secret");
    }

    private StorageBootstrapState state(boolean completed, String activeProvider) {
        return StorageBootstrapState.builder().id(1).legacyImportCompleted(completed)
                .legacyActiveProvider(activeProvider).build();
    }

    private StorageProviderConfig localDefault(Long id) {
        return StorageProviderConfig.builder().id(id).configName("本地默认配置").provider("local")
                .localRoot("C:/seeded/uploads").publicUrl("/uploads/").configSource("DEFAULT")
                .active(true).build();
    }

    private StorageProviderConfig cloud(Long id, String provider) {
        return StorageProviderConfig.builder().id(id).configName(provider).provider(provider)
                .endpoint("https://storage.example.test").region("region-1").bucket("bucket")
                .publicUrl("https://cdn.example.test/").accessKeyIdCiphertext("v1:key")
                .accessKeySecretCiphertext("v1:secret").build();
    }

    private String base64Key() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
