package com.wzh.blog.vo;

/** Provider capability state without endpoints, identifiers, or secrets. */
public record StorageProviderStatusVO(
        String provider,
        boolean active,
        boolean configured,
        boolean credentialsConfigured,
        boolean supportsValidation,
        int usableProfileCount) {

    /** @deprecated Compatibility constructor for callers that predate profiles. */
    @Deprecated
    public StorageProviderStatusVO(String provider, boolean active, boolean configured,
                                   boolean credentialsConfigured, boolean supportsValidation) {
        this(provider, active, configured, credentialsConfigured, supportsValidation,
                configured ? 1 : 0);
    }
}
