package com.wzh.blog.media;

import java.util.Locale;

/**
 * Provider identity persisted with every asset. The value is deliberately
 * independent of any vendor SDK class.
 */
public enum StorageProviderType {
    LOCAL("local"),
    OSS("oss"),
    COS("cos"),
    TOS("tos");

    private final String code;

    StorageProviderType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static StorageProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Storage provider must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StorageProviderType provider : values()) {
            if (provider.code.equals(normalized)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported storage provider: " + value);
    }
}
