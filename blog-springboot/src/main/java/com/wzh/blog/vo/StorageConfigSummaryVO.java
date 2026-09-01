package com.wzh.blog.vo;

/**
 * Administrator-visible storage-profile metadata. This intentionally has no
 * credential, ciphertext, signature, authorization, or request URL fields.
 */
public record StorageConfigSummaryVO(
        Long id,
        String name,
        String provider,
        boolean active,
        boolean configured,
        boolean credentialsConfigured,
        String endpoint,
        String region,
        String bucket,
        String localRoot,
        String publicUrl,
        StorageValidationVO validation,
        StorageUsageVO usage) {
}
