package com.wzh.blog.infrastructure.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.wzh.blog.config.StorageProperties;
import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderType;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class CosStorageProvider implements StorageProvider {

    private final StorageProperties.Provider properties;
    private volatile COSClient client;

    public CosStorageProvider(StorageProperties properties) {
        this(properties.getCos());
    }

    CosStorageProvider(StorageProperties.Provider properties) {
        this.properties = properties;
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.COS;
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public StorageObjectMetadata put(String objectKey, InputStream content, long size, String contentType)
            throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        StorageProviderSupport.DigestingInputStream digesting = StorageProviderSupport.digesting(content);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        metadata.setContentType(contentType);
        try {
            client().putObject(properties.getBucket(), objectKey, digesting, metadata);
            if (digesting.count() != size) {
                delete(objectKey);
                throw new IOException("Object stream size does not match declared size");
            }
            return metadata(objectKey, contentType, size, digesting.checksum(), Instant.now());
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("COS upload failed", exception);
        }
    }

    @Override
    public StorageObject get(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            COSObject object = client().getObject(properties.getBucket(), objectKey);
            ObjectMetadata metadata = object.getObjectMetadata();
            return new StorageObject(
                    StorageProviderSupport.closeWith(object.getObjectContent(), object),
                    metadata(objectKey, metadata.getContentType(), metadata.getContentLength(),
                            valueOrEtag(metadata.getETag()), instant(metadata.getLastModified())));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("COS read failed", exception);
        }
    }

    @Override
    public StorageObjectMetadata head(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            ObjectMetadata metadata = client().getObjectMetadata(properties.getBucket(), objectKey);
            return metadata(objectKey, metadata.getContentType(), metadata.getContentLength(),
                    valueOrEtag(metadata.getETag()), instant(metadata.getLastModified()));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("COS head failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().deleteObject(properties.getBucket(), objectKey);
        } catch (CosServiceException exception) {
            if (isMissingObject(exception)) {
                return;
            }
            throw StorageProviderSupport.asIOException("COS delete failed", exception);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("COS delete failed", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            return client().doesObjectExist(properties.getBucket(), objectKey);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("COS exists failed", exception);
        }
    }

    @Override
    public void validateConnection() {
        requireConfigured();
        String key = ".health/" + UUID.randomUUID() + ".txt";
        byte[] body = "storage-health".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            put(key, new java.io.ByteArrayInputStream(body), body.length, "text/plain");
            try (StorageObject object = get(key)) {
                if (!java.util.Arrays.equals(body, object.content().readAllBytes())) {
                    throw new IOException("COS read verification failed");
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("COS validation failed", exception);
        } finally {
            try {
                delete(key);
            } catch (IOException exception) {
                throw new IllegalStateException("COS validation cleanup failed", exception);
            }
        }
    }

    private COSClient client() {
        requireConfigured();
        COSClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    COSCredentials credentials = new BasicCOSCredentials(
                            properties.getAccessKeyId(), properties.getAccessKeySecret());
                    ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
                    config.setConnectionTimeout(3000);
                    config.setSocketTimeout(5000);
                    config.setMaxErrorRetry(2);
                    current = new COSClient(credentials, config);
                    client = current;
                }
            }
        }
        return current;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("COS storage provider is not configured");
        }
    }

    private StorageObjectMetadata metadata(String objectKey, String contentType, long size,
                                           String checksum, Instant lastModified) {
        return new StorageObjectMetadata(
                objectKey,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                size,
                checksum == null || checksum.isBlank() ? "unknown" : checksum,
                lastModified);
    }

    private String valueOrEtag(String etag) {
        return etag == null || etag.isBlank() ? "unknown" : etag;
    }

    private Instant instant(Date date) {
        return date == null ? Instant.now() : date.toInstant();
    }

    private boolean isMissingObject(CosServiceException exception) {
        String code = exception.getErrorCode();
        String message = exception.getMessage();
        return exception.getStatusCode() == 404
                || "NoSuchKey".equalsIgnoreCase(code)
                || "NoSuchObject".equalsIgnoreCase(code)
                || (message != null && (message.contains("NoSuchKey") || message.contains("404")));
    }

    @PreDestroy
    void close() {
        COSClient current = client;
        if (current != null) {
            current.shutdown();
        }
    }
}
