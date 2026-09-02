package com.wzh.blog.media;

import java.util.Objects;

public record StorageProviderConfigSnapshot(
        Long id,
        String name,
        StorageProviderType provider,
        String endpoint,
        String bucket,
        String region,
        String localRoot,
        String publicUrl,
        String accessKeyId,
        String accessKeySecret,
        CredentialSupplier credentialSupplier) {

    /**
     * Compatibility constructor for legacy callers and focused tests that
     * provide plaintext credentials directly. Managed profiles use the
     * credential-supplier constructor below and keep these fields null.
     */
    public StorageProviderConfigSnapshot(Long id, String name, StorageProviderType provider,
                                         String endpoint, String bucket, String region, String localRoot,
                                         String publicUrl, String accessKeyId, String accessKeySecret) {
        this(id, name, provider, endpoint, bucket, region, localRoot, publicUrl,
                accessKeyId, accessKeySecret, null);
    }

    /** Creates a managed profile that contains no plaintext credential fields. */
    public static StorageProviderConfigSnapshot managed(Long id, String name, StorageProviderType provider,
                                                        String endpoint, String bucket, String region,
                                                        String localRoot, String publicUrl,
                                                        CredentialSupplier credentialSupplier) {
        return new StorageProviderConfigSnapshot(id, name, provider, endpoint, bucket, region,
                localRoot, publicUrl, null, null,
                Objects.requireNonNull(credentialSupplier, "credentialSupplier"));
    }

    /** Does not resolve credentials; safe for provider configuration checks. */
    public boolean credentialsConfigured() {
        if (credentialSupplier != null) {
            try {
                return credentialSupplier.configured();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return hasText(accessKeyId) && hasText(accessKeySecret);
    }

    /** Resolves credentials only at the SDK client construction boundary. */
    public Credentials resolveCredentials() {
        if (credentialSupplier != null) {
            return Objects.requireNonNull(credentialSupplier.resolve(),
                    "Storage credential supplier returned null");
        }
        if (!credentialsConfigured()) {
            return null;
        }
        return new Credentials(accessKeyId, accessKeySecret);
    }

    @Override
    public String toString() {
        return "StorageProviderConfigSnapshot{id=" + id
                + ", name='" + name + '\''
                + ", provider=" + provider
                + '}';
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public interface CredentialSupplier {

        boolean configured();

        Credentials resolve();
    }

    /** Short-lived SDK input; its diagnostic representation is always redacted. */
    public record Credentials(String accessKeyId, String accessKeySecret) {

        @Override
        public String toString() {
            return "StorageProviderCredentials[REDACTED]";
        }
    }
}
