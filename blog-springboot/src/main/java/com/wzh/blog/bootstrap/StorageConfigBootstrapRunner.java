package com.wzh.blog.bootstrap;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.config.StorageProperties;
import com.wzh.blog.dao.StorageBootstrapStateDao;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageBootstrapState;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Imports deployment-era storage variables once into managed storage profiles. */
@Component
@Order(0)
public class StorageConfigBootstrapRunner implements ApplicationRunner {

    private static final String LEGACY_SOURCE = "LEGACY_ENV";
    private static final String DEFAULT_SOURCE = "DEFAULT";
    private static final String CONTENT_BACKFILL = """
            UPDATE tb_content_asset
            SET storage_config_id = ?
            WHERE storage_config_id IS NULL AND provider = ?
            """;
    private static final String MEDIA_BACKFILL = """
            UPDATE tb_media_asset
            SET storage_config_id = ?
            WHERE storage_config_id IS NULL AND storage_mode = ?
            """;

    private final StorageBootstrapStateDao stateDao;
    private final StorageProviderConfigDao configDao;
    private final StorageConfigCrypto crypto;
    private final StorageProperties legacyProperties;
    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry registry;

    public StorageConfigBootstrapRunner(StorageBootstrapStateDao stateDao,
                                        StorageProviderConfigDao configDao,
                                        StorageConfigCrypto crypto,
                                        StorageProperties legacyProperties,
                                        JdbcTemplate jdbcTemplate,
                                        StorageProviderRegistry registry) {
        this.stateDao = stateDao;
        this.configDao = configDao;
        this.crypto = crypto;
        this.legacyProperties = legacyProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.registry = registry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        StorageBootstrapState state = stateDao.selectForUpdate();
        if (state == null) {
            throw new IllegalStateException("Storage bootstrap state is unavailable");
        }
        if (Boolean.TRUE.equals(state.getLegacyImportCompleted())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        importConfiguredLegacyProfiles(now);
        List<StorageProviderConfig> catalog = configDao.selectAll();
        StorageProviderConfig activeProfile = selectActiveProfile(state, catalog);
        configDao.activateOnly(activeProfile.getId(), null, now);
        backfillLegacyAssets(catalog);
        stateDao.markCompleted(now);
        refreshRegistryAfterCommit(activeProfile.getId());
    }

    private void importConfiguredLegacyProfiles(LocalDateTime now) {
        if (isUsableLocal(legacyProperties.getLocalRoot(), legacyProperties.getLocalPublicUrl())) {
            configDao.upsertLegacyProfile(StorageProviderConfig.builder()
                    .configName("旧环境本地存储")
                    .provider(StorageProviderType.LOCAL.code())
                    .localRoot(legacyProperties.getLocalRoot())
                    .publicUrl(legacyProperties.getLocalPublicUrl())
                    .configSource(LEGACY_SOURCE)
                    .lastValidationStatus("NEVER")
                    .usageStatus("NEVER")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        importCloudProfile(StorageProviderType.COS, legacyProperties.getCos(), now);
        importCloudProfile(StorageProviderType.OSS, legacyProperties.getOss(), now);
        importCloudProfile(StorageProviderType.TOS, legacyProperties.getTos(), now);
    }

    private void importCloudProfile(StorageProviderType provider,
                                    StorageProperties.Provider legacy,
                                    LocalDateTime now) {
        if (!legacy.configured()) {
            return;
        }
        configDao.upsertLegacyProfile(StorageProviderConfig.builder()
                .configName("旧环境 " + provider.code().toUpperCase() + " 存储")
                .provider(provider.code())
                .endpoint(legacy.getEndpoint())
                .region(legacy.getRegion())
                .bucket(legacy.getBucket())
                .publicUrl(legacy.getPublicUrl())
                .accessKeyIdCiphertext(crypto.encrypt(legacy.getAccessKeyId()))
                .accessKeySecretCiphertext(crypto.encrypt(legacy.getAccessKeySecret()))
                .configSource(LEGACY_SOURCE)
                .lastValidationStatus("NEVER")
                .usageStatus("NEVER")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private StorageProviderConfig selectActiveProfile(StorageBootstrapState state,
                                                      List<StorageProviderConfig> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            throw new IllegalStateException("No storage profile is configured");
        }
        String legacyProvider = state.getLegacyActiveProvider();
        StorageProviderConfig selected = catalog.stream()
                .filter(profile -> LEGACY_SOURCE.equals(profile.getConfigSource()))
                .filter(profile -> sameProvider(profile, legacyProvider))
                .filter(this::isUsable)
                .findFirst()
                .orElseGet(() -> catalog.stream()
                        .filter(profile -> StorageProviderType.LOCAL.code().equalsIgnoreCase(profile.getProvider()))
                        .filter(profile -> DEFAULT_SOURCE.equals(profile.getConfigSource()))
                        .filter(this::isUsable)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No usable local storage profile is configured")));
        if (selected.getId() == null) {
            throw new IllegalStateException("Storage profile ID is missing");
        }
        return selected;
    }

    private void backfillLegacyAssets(List<StorageProviderConfig> catalog) {
        Map<StorageProviderType, List<StorageProviderConfig>> usableProfiles = new EnumMap<>(StorageProviderType.class);
        for (StorageProviderConfig profile : catalog) {
            StorageProviderType type = typeOf(profile);
            if (type != null && isUsable(profile)) {
                usableProfiles.computeIfAbsent(type, ignored -> new ArrayList<>()).add(profile);
            }
        }
        usableProfiles.forEach((type, profiles) -> {
            List<StorageProviderConfig> candidates = backfillCandidates(type, profiles);
            if (candidates.size() == 1) {
                Long id = candidates.getFirst().getId();
                jdbcTemplate.update(CONTENT_BACKFILL, id, type.code());
                jdbcTemplate.update(MEDIA_BACKFILL, id, type.code());
            }
        });
    }

    private List<StorageProviderConfig> backfillCandidates(StorageProviderType type,
                                                           List<StorageProviderConfig> profiles) {
        if (type != StorageProviderType.LOCAL || profiles.stream()
                .noneMatch(profile -> LEGACY_SOURCE.equals(profile.getConfigSource()))) {
            return profiles;
        }
        return profiles.stream()
                .filter(profile -> !DEFAULT_SOURCE.equals(profile.getConfigSource()))
                .toList();
    }

    private void refreshRegistryAfterCommit(Long configId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registry.refresh(configId);
                }
            });
            return;
        }
        registry.refresh(configId);
    }

    private boolean sameProvider(StorageProviderConfig profile, String provider) {
        return provider != null && profile != null && profile.getProvider() != null
                && provider.equalsIgnoreCase(profile.getProvider());
    }

    private boolean isUsable(StorageProviderConfig profile) {
        StorageProviderType provider = typeOf(profile);
        if (provider == null) {
            return false;
        }
        return provider == StorageProviderType.LOCAL
                ? isUsableLocal(profile.getLocalRoot(), profile.getPublicUrl())
                : hasText(profile.getEndpoint()) && hasText(profile.getRegion()) && hasText(profile.getBucket())
                && hasText(profile.getPublicUrl()) && hasText(profile.getAccessKeyIdCiphertext())
                && hasText(profile.getAccessKeySecretCiphertext());
    }

    private StorageProviderType typeOf(StorageProviderConfig profile) {
        if (profile == null || profile.getProvider() == null) {
            return null;
        }
        try {
            return StorageProviderType.from(profile.getProvider());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isUsableLocal(String root, String publicUrl) {
        return hasText(root) && hasText(publicUrl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
