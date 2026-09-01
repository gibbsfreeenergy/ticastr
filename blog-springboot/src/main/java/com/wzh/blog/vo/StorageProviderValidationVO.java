package com.wzh.blog.vo;

/** Safe result of an ephemeral provider write/read/delete check. */
public record StorageProviderValidationVO(
        String provider,
        Long configId,
        boolean success,
        boolean write,
        boolean read,
        boolean delete,
        String message) {

    /** @deprecated Compatibility constructor for provider-only validation. */
    @Deprecated
    public StorageProviderValidationVO(String provider, boolean success, boolean write,
                                       boolean read, boolean delete, String message) {
        this(provider, null, success, write, read, delete, message);
    }
}
