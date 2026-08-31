package com.wzh.blog.media;

import com.wzh.blog.config.StorageProperties;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.infrastructure.storage.StorageProviderFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves lazy provider adapters by managed profile ID. The compatibility
 * constructors remain only until legacy asset rows gain storage_config_id.
 */
public class StorageProviderRegistry {

    private final StorageProviderConfigDao configDao;
    private final StorageProviderFactory factory;
    private final ConcurrentHashMap<Long, StorageProvider> providersByConfigId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StorageProviderConfig> configsById = new ConcurrentHashMap<>();
    private final AtomicReference<StorageProviderConfig> activeConfig;
    private final Map<StorageProviderType, StorageProvider> legacyProviders;
    private final AtomicReference<StorageProviderType> legacyActiveProvider;
    private final StorageProperties legacyProperties;

    public StorageProviderRegistry(StorageProviderConfigDao configDao, StorageProviderFactory factory) {
        this.configDao = Objects.requireNonNull(configDao, "configDao");
        this.factory = Objects.requireNonNull(factory, "factory");
        StorageProviderConfig selected = requireConfig(configDao.selectActive(), "No active storage profile is configured");
        cacheConfig(selected);
        this.activeConfig = new AtomicReference<>(selected);
        this.legacyProviders = Map.of();
        this.legacyActiveProvider = new AtomicReference<>();
        this.legacyProperties = null;
    }

    /** @deprecated Temporary compatibility for provider-only asset records. */
    @Deprecated
    public StorageProviderRegistry(StorageProviderType initialProvider,
                                   Collection<StorageProvider> providerCollection) {
        this(initialProvider, providerCollection, null);
    }

    /** @deprecated Temporary compatibility for provider-only asset records. */
    @Deprecated
    public StorageProviderRegistry(StorageProviderType initialProvider,
                                   Collection<StorageProvider> providerCollection,
                                   StorageProperties properties) {
        Objects.requireNonNull(initialProvider, "initialProvider");
        Objects.requireNonNull(providerCollection, "providerCollection");
        EnumMap<StorageProviderType, StorageProvider> byType = new EnumMap<>(StorageProviderType.class);
        for (StorageProvider provider : providerCollection) {
            if (provider != null) {
                StorageProvider previous = byType.put(provider.type(), provider);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate storage provider: " + provider.type());
                }
            }
        }
        if (!byType.containsKey(initialProvider)) {
            throw new IllegalArgumentException("Active storage provider is not registered: " + initialProvider.code());
        }
        this.configDao = null;
        this.factory = null;
        this.activeConfig = new AtomicReference<>();
        this.legacyProviders = Map.copyOf(byType);
        this.legacyActiveProvider = new AtomicReference<>(initialProvider);
        this.legacyProperties = properties;
    }

    /** @deprecated Temporary compatibility for provider-only asset records. */
    @Deprecated
    public StorageProviderRegistry(StorageProviderType initialProvider, StorageProvider... providers) {
        this(initialProvider, List.of(providers));
    }

    public StorageProvider providerForNewAsset() {
        StorageProviderConfig current = activeConfig.get();
        return current == null ? providerFor(legacyActiveProvider.get()) : providerForConfig(current.getId());
    }

    public StorageProvider providerForConfig(Long configId) {
        return providersByConfigId.computeIfAbsent(requireId(configId), ignored -> factory.create(configFor(configId)));
    }

    public StorageProvider providerForLegacyProvider(StorageProviderType providerType) {
        Objects.requireNonNull(providerType, "providerType");
        if (configDao == null) {
            return providerFor(providerType);
        }
        List<StorageProviderConfig> matching = configDao.selectAll().stream()
                .filter(config -> providerType.code().equalsIgnoreCase(config.getProvider()))
                .filter(this::isConfigured)
                .toList();
        if (matching.size() != 1) {
            throw new IllegalStateException("Legacy storage provider is ambiguous: " + providerType.code());
        }
        StorageProviderConfig config = matching.getFirst();
        cacheConfig(config);
        return providerForConfig(config.getId());
    }

    public StorageProviderConfig activeConfig() {
        StorageProviderConfig current = activeConfig.get();
        if (current == null) {
            throw new IllegalStateException("No managed storage profile is configured");
        }
        return current;
    }

    public Long activeConfigId() {
        return activeConfig().getId();
    }

    /** Builds a public reference without exposing credentials. */
    public String publicReference(Long configId, String objectKey) {
        ObjectKeyPolicy.requireSafe(objectKey);
        String base = publicBase(configId);
        return base.isBlank() ? objectKey : base + objectKey;
    }

    public String publicBase(Long configId) {
        String configuredBase = configFor(configId).getPublicUrl();
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new IllegalStateException("Public URL is not configured for storage profile " + configId);
        }
        return withTrailingSlash(configuredBase);
    }

    public void invalidate(Long configId) {
        Long id = requireId(configId);
        StorageProvider provider = providersByConfigId.remove(id);
        configsById.remove(id);
        if (provider != null) {
            provider.close();
        }
        StorageProviderConfig current = activeConfig.get();
        if (current != null && id.equals(current.getId())) {
            activeConfig.set(loadConfig(id));
        }
    }

    public void refresh(Long configId) {
        StorageProviderConfig selected = loadConfig(requireId(configId));
        activeConfig.set(selected);
    }

    /** @deprecated Resolves a provider-only legacy asset only when unambiguous. */
    @Deprecated
    public StorageProvider providerFor(StorageProviderType providerType) {
        Objects.requireNonNull(providerType, "providerType");
        if (configDao != null) {
            return providerForLegacyProvider(providerType);
        }
        StorageProvider provider = legacyProviders.get(providerType);
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider is not registered: " + providerType.code());
        }
        return provider;
    }

    /** @deprecated Use activeConfigId() for managed storage profiles. */
    @Deprecated
    public StorageProviderType activeProviderType() {
        StorageProviderConfig current = activeConfig.get();
        return current == null ? legacyActiveProvider.get() : StorageProviderType.from(current.getProvider());
    }

    /** @deprecated Use refresh(Long) for managed storage profiles. */
    @Deprecated
    public void refresh(StorageProviderType providerType) {
        Objects.requireNonNull(providerType, "providerType");
        if (configDao == null) {
            providerFor(providerType);
            legacyActiveProvider.set(providerType);
            return;
        }
        StorageProviderConfig current = activeConfig();
        if (providerType == StorageProviderType.from(current.getProvider())) {
            return;
        }
        List<StorageProviderConfig> matching = configDao.selectAll().stream()
                .filter(config -> providerType.code().equalsIgnoreCase(config.getProvider()))
                .filter(this::isConfigured)
                .toList();
        if (matching.size() != 1) {
            throw new IllegalStateException("Storage provider profile is ambiguous: " + providerType.code());
        }
        refresh(matching.getFirst().getId());
    }

    /** @deprecated Use publicReference(Long, String) for managed storage profiles. */
    @Deprecated
    public String publicReference(StorageProviderType providerType, String objectKey) {
        ObjectKeyPolicy.requireSafe(objectKey);
        return publicBase(providerType) + objectKey;
    }

    /** @deprecated Use publicBase(Long) for managed storage profiles. */
    @Deprecated
    public String publicBase(StorageProviderType providerType) {
        if (configDao != null) {
            StorageProviderConfig current = activeConfig.get();
            if (current != null && providerType.code().equalsIgnoreCase(current.getProvider())) {
                return publicBase(current.getId());
            }
            List<StorageProviderConfig> matching = configDao.selectAll().stream()
                    .filter(config -> providerType.code().equalsIgnoreCase(config.getProvider()))
                    .filter(this::isConfigured)
                    .toList();
            if (matching.size() != 1) {
                throw new IllegalStateException("Legacy storage provider is ambiguous: " + providerType.code());
            }
            return publicBase(matching.getFirst().getId());
        }
        if (legacyProperties == null) {
            return "";
        }
        String configuredBase = switch (providerType) {
            case LOCAL -> legacyProperties.getLocalPublicUrl();
            case OSS -> legacyProperties.getOss().getPublicUrl();
            case COS -> legacyProperties.getCos().getPublicUrl();
            case TOS -> legacyProperties.getTos().getPublicUrl();
        };
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new IllegalStateException("Public URL is not configured for provider " + providerType.code());
        }
        return withTrailingSlash(configuredBase);
    }

    private StorageProviderConfig configFor(Long configId) {
        return configsById.computeIfAbsent(requireId(configId), this::loadConfig);
    }

    private StorageProviderConfig loadConfig(Long configId) {
        return cacheConfig(requireConfig(configDao.selectById(configId), "Storage profile does not exist: " + configId));
    }

    private StorageProviderConfig cacheConfig(StorageProviderConfig config) {
        if (config.getId() == null) {
            throw new IllegalStateException("Storage profile ID is missing");
        }
        configsById.put(config.getId(), config);
        return config;
    }

    private StorageProviderConfig requireConfig(StorageProviderConfig config, String message) {
        if (config == null) {
            throw new IllegalStateException(message);
        }
        return config;
    }

    private Long requireId(Long configId) {
        if (configId == null) {
            throw new IllegalArgumentException("Storage profile ID must not be null");
        }
        return configId;
    }

    private boolean isConfigured(StorageProviderConfig config) {
        if (config == null || config.getProvider() == null) {
            return false;
        }
        StorageProviderType provider;
        try {
            provider = StorageProviderType.from(config.getProvider());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return provider == StorageProviderType.LOCAL
                ? hasText(config.getLocalRoot())
                : hasText(config.getEndpoint()) && hasText(config.getBucket()) && hasText(config.getRegion())
                && hasText(config.getAccessKeyIdCiphertext()) && hasText(config.getAccessKeySecretCiphertext());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String withTrailingSlash(String base) {
        return base.endsWith("/") ? base : base + "/";
    }
}
