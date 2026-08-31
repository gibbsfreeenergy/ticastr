package com.wzh.blog.media;

import java.io.IOException;
import java.io.InputStream;

/**
 * Common object-storage contract. Vendor SDKs must not cross this boundary.
 */
public interface StorageProvider {

    StorageProviderType type();

    StorageObjectMetadata put(String objectKey, InputStream content, long size, String contentType)
            throws IOException;

    StorageObject get(String objectKey) throws IOException;

    StorageObjectMetadata head(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    boolean exists(String objectKey) throws IOException;

    void validateConnection();

    /** Configuration presence is safe to expose as a boolean to administrators. */
    default boolean configured() {
        return true;
    }

    default String requireSafeKey(String objectKey) {
        return ObjectKeyPolicy.requireSafe(objectKey);
    }
}
