package com.wzh.blog.media;

import com.wzh.blog.config.StorageProperties;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the provider for new objects while preserving provider identity for
 * existing assets. Construction and refresh are local operations; adapters
 * perform network work only when an object operation or explicit validation is
 * requested.
 */
public class StorageProviderRegistry {

    private final Map<StorageProviderType, StorageProvider> providers;
    private final AtomicReference<StorageProviderType> activeProvider;
    private final StorageProperties properties;

    public StorageProviderRegistry(StorageProviderType initialProvider,
                                   Collection<StorageProvider> providerCollection) {
        this(initialProvider, providerCollection, null);
    }

    public StorageProviderRegistry(StorageProviderType initialProvider,
                                   Collection<StorageProvider> providerCollection,
                                   StorageProperties properties) {
        Objects.requireNonNull(initialProvider, "initialProvider");
        Objects.requireNonNull(providerCollection, "providerCollection");
        EnumMap<StorageProviderType, StorageProvider> byType = new EnumMap<>(StorageProviderType.class);
        for (StorageProvider provider : providerCollection) {
            if (provider == null) {
                continue;
            }
            StorageProvider previous = byType.put(provider.type(), provider);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate storage provider: " + provider.type());
            }
        }
        if (!byType.containsKey(initialProvider)) {
            throw new IllegalArgumentException("Active storage provider is not registered: " + initialProvider.code());
        }
        this.providers = Map.copyOf(byType);
        this.activeProvider = new AtomicReference<>(initialProvider);
        this.properties = properties;
    }

    public StorageProviderRegistry(StorageProviderType initialProvider, StorageProvider... providers) {
        this(initialProvider, List.of(providers));
    }

    public StorageProvider providerForNewAsset() {
        return providerFor(activeProvider.get());
    }

    public StorageProvider providerFor(StorageProviderType providerType) {
        StorageProvider provider = providers.get(Objects.requireNonNull(providerType, "providerType"));
        if (provider == null) {
            throw new IllegalArgumentException("Storage provider is not registered: " + providerType.code());
        }
        return provider;
    }

    public StorageProviderType activeProviderType() {
        return activeProvider.get();
    }

    public void refresh(StorageProviderType providerType) {
        providerFor(providerType);
        activeProvider.set(providerType);
    }

    public Map<StorageProviderType, StorageProvider> providers() {
        return providers;
    }

    /** Builds a public URL/reference without exposing provider credentials. */
    public String publicReference(StorageProviderType providerType, String objectKey) {
        ObjectKeyPolicy.requireSafe(objectKey);
        String base = publicBase(providerType);
        return base.isBlank() ? objectKey : base + objectKey;
    }

    public String publicBase(StorageProviderType providerType) {
        if (properties == null) {
            return "";
        }
        String configuredBase = switch (providerType) {
            case LOCAL -> properties.getLocalPublicUrl();
            case OSS -> properties.getOss().getPublicUrl();
            case COS -> properties.getCos().getPublicUrl();
            case TOS -> properties.getTos().getPublicUrl();
        };
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new IllegalStateException("Public URL is not configured for provider " + providerType.code());
        }
        return configuredBase.endsWith("/") ? configuredBase : configuredBase + "/";
    }
}
