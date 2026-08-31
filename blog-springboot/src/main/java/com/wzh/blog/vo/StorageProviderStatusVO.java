package com.wzh.blog.vo;

/** Provider capability state without endpoints, identifiers, or secrets. */
public record StorageProviderStatusVO(
        String provider,
        boolean active,
        boolean configured,
        boolean credentialsConfigured,
        boolean supportsValidation) {
}
