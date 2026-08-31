package com.wzh.blog.infrastructure.storage;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderConfigSnapshot;
import com.wzh.blog.media.StorageProviderType;

import java.nio.file.Path;
import java.util.Objects;

/** Creates one lazy adapter from one managed storage profile. */
public class StorageProviderFactory {

    private final StorageConfigCrypto crypto;

    public StorageProviderFactory(StorageConfigCrypto crypto) {
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    public StorageProvider create(StorageProviderConfig config) {
        return create(snapshot(config));
    }

    public StorageProvider create(StorageProviderConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return switch (snapshot.provider()) {
            case LOCAL -> new LocalStorageProvider(Path.of(requireText(snapshot.localRoot(), "localRoot")));
            case OSS -> new OssStorageProvider(snapshot);
            case COS -> new CosStorageProvider(snapshot);
            case TOS -> new TosStorageProvider(snapshot);
        };
    }

    private StorageProviderConfigSnapshot snapshot(StorageProviderConfig config) {
        Objects.requireNonNull(config, "config");
        StorageProviderType provider = StorageProviderType.from(config.getProvider());
        String accessKeyId = null;
        String accessKeySecret = null;
        if (provider != StorageProviderType.LOCAL) {
            accessKeyId = decrypt(config.getAccessKeyIdCiphertext());
            accessKeySecret = decrypt(config.getAccessKeySecretCiphertext());
        }
        return new StorageProviderConfigSnapshot(
                config.getId(), config.getConfigName(), provider, config.getEndpoint(), config.getBucket(),
                config.getRegion(), config.getLocalRoot(), config.getPublicUrl(), accessKeyId, accessKeySecret);
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        return crypto.decrypt(ciphertext);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Storage profile " + field + " must not be blank");
        }
        return value;
    }
}
