package com.wzh.blog.media;

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
        String accessKeySecret) {

    @Override
    public String toString() {
        return "StorageProviderConfigSnapshot{id=" + id
                + ", name='" + name + '\''
                + ", provider=" + provider
                + '}';
    }
}
