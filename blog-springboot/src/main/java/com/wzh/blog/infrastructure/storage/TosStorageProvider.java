package com.wzh.blog.infrastructure.storage;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.DeleteObjectInput;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.GetObjectV2Output;
import com.volcengine.tos.model.object.HeadObjectV2Input;
import com.volcengine.tos.model.object.HeadObjectV2Output;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.PutObjectInput;
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

/**
 * Volcengine TOS adapter. The SDK client is lazy so inactive providers do not
 * open sockets or validate credentials during application startup.
 */
public final class TosStorageProvider implements StorageProvider {

    private final StorageProperties.Provider properties;
    private volatile TOSV2 client;

    public TosStorageProvider(StorageProperties properties) {
        this(properties.getTos());
    }

    TosStorageProvider(StorageProperties.Provider properties) {
        this.properties = properties;
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.TOS;
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
        ObjectMetaRequestOptions options = new ObjectMetaRequestOptions().setContentType(contentType);
        PutObjectInput input = new PutObjectInput()
                .setBucket(properties.getBucket())
                .setKey(objectKey)
                .setContent(digesting)
                .setContentLength(size)
                .setOptions(options)
                .setForbidOverwrite(true);
        try {
            client().putObject(input);
            if (digesting.count() != size) {
                delete(objectKey);
                throw new IOException("Object stream size does not match declared size");
            }
            return metadata(objectKey, contentType, size, digesting.checksum(), Instant.now());
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("TOS upload failed", exception);
        }
    }

    @Override
    public StorageObject get(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            GetObjectV2Output output = client().getObject(new GetObjectV2Input()
                    .setBucket(properties.getBucket())
                    .setKey(objectKey));
            return new StorageObject(
                    StorageProviderSupport.closeWith(output.getContent(), output),
                    metadata(objectKey, output.getContentType(), output.getContentLength(),
                            valueOrEtag(output.getEtag()), instant(output.getLastModifiedInDate())));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("TOS read failed", exception);
        }
    }

    @Override
    public StorageObjectMetadata head(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            HeadObjectV2Output output = client().headObject(new HeadObjectV2Input()
                    .setBucket(properties.getBucket())
                    .setKey(objectKey));
            return metadata(objectKey, output.getContentType(), output.getContentLength(),
                    valueOrEtag(output.getEtag()), instant(output.getLastModifiedInDate()));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("TOS head failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().deleteObject(new DeleteObjectInput()
                    .setBucket(properties.getBucket())
                    .setKey(objectKey));
        } catch (TosServerException exception) {
            if (isMissingObject(exception)) {
                return;
            }
            throw StorageProviderSupport.asIOException("TOS delete failed", exception);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.asIOException("TOS delete failed", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().headObject(new HeadObjectV2Input()
                    .setBucket(properties.getBucket())
                    .setKey(objectKey));
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && (message.contains("404") || message.contains("NoSuchKey")
                    || message.contains("NotFound"))) {
                return false;
            }
            throw StorageProviderSupport.asIOException("TOS exists failed", exception);
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
                    throw new IOException("TOS read verification failed");
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("TOS validation failed", exception);
        } finally {
            try {
                delete(key);
            } catch (IOException exception) {
                throw new IllegalStateException("TOS validation cleanup failed", exception);
            }
        }
    }

    private TOSV2 client() {
        requireConfigured();
        TOSV2 current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new TOSV2ClientBuilder().build(
                            properties.getRegion(),
                            properties.getEndpoint(),
                            properties.getAccessKeyId(),
                            properties.getAccessKeySecret());
                    client = current;
                }
            }
        }
        return current;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("TOS storage provider is not configured");
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

    private boolean isMissingObject(TosServerException exception) {
        String code = exception.getCode();
        String message = exception.getMessage();
        return exception.getStatusCode() == 404
                || "NoSuchKey".equalsIgnoreCase(code)
                || "NoSuchObject".equalsIgnoreCase(code)
                || (message != null && (message.contains("NoSuchKey") || message.contains("404")));
    }

    @PreDestroy
    void close() {
        TOSV2 current = client;
        if (current != null) {
            try {
                current.close();
            } catch (IOException exception) {
                // Shutdown must not prevent the Spring context from closing.
            }
        }
    }
}
