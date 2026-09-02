package com.wzh.blog.infrastructure.storage;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosServerException;
import com.volcengine.tos.model.object.DeleteObjectInput;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.GetObjectV2Output;
import com.volcengine.tos.model.object.HeadObjectV2Input;
import com.volcengine.tos.model.object.HeadObjectV2Output;
import com.volcengine.tos.model.object.ListObjectsType2Input;
import com.volcengine.tos.model.object.ListObjectsType2Output;
import com.volcengine.tos.model.object.ListedObjectV2;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.transport.TransportConfig;
import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderConfigSnapshot;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.media.StorageUsage;
import com.wzh.blog.media.StorageProviderConfigSnapshot.Credentials;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Volcengine TOS adapter with a lazy profile-scoped SDK client. */
public final class TosStorageProvider implements StorageProvider {

    private static final int USAGE_PAGE_SIZE = 1000;
    private static final int MAX_USAGE_PAGES = 10_000;
    private static final Duration MAX_USAGE_DURATION = Duration.ofSeconds(30);

    private final StorageProviderConfigSnapshot profile;
    private volatile TOSV2 client;
    private boolean closed;

    public TosStorageProvider(StorageProviderConfigSnapshot profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        if (profile.provider() != StorageProviderType.TOS) {
            throw new IllegalArgumentException("Storage profile is not a TOS profile");
        }
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.TOS;
    }

    @Override
    public boolean configured() {
        return hasText(profile.endpoint()) && hasText(profile.bucket()) && hasText(profile.region())
                && profile.credentialsConfigured();
    }

    @Override
    public StorageObjectMetadata put(String objectKey, InputStream content, long size, String contentType)
            throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        StorageProviderSupport.DigestingInputStream digesting = StorageProviderSupport.digesting(content);
        ObjectMetaRequestOptions options = new ObjectMetaRequestOptions().setContentType(contentType);
        PutObjectInput input = new PutObjectInput()
                .setBucket(profile.bucket())
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
            throw StorageProviderSupport.sanitizedOperationIOException("TOS upload failed", exception);
        }
    }

    @Override
    public StorageObject get(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            GetObjectV2Output output = client().getObject(new GetObjectV2Input().setBucket(profile.bucket()).setKey(objectKey));
            return new StorageObject(StorageProviderSupport.closeWith(output.getContent(), output),
                    metadata(objectKey, output.getContentType(), output.getContentLength(),
                            valueOrEtag(output.getEtag()), instant(output.getLastModifiedInDate())));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("TOS read failed", exception);
        }
    }

    @Override
    public StorageObjectMetadata head(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            HeadObjectV2Output output = client().headObject(new HeadObjectV2Input().setBucket(profile.bucket()).setKey(objectKey));
            return metadata(objectKey, output.getContentType(), output.getContentLength(),
                    valueOrEtag(output.getEtag()), instant(output.getLastModifiedInDate()));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("TOS head failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().deleteObject(new DeleteObjectInput().setBucket(profile.bucket()).setKey(objectKey));
        } catch (TosServerException exception) {
            if (isMissingObject(exception)) {
                return;
            }
            throw StorageProviderSupport.sanitizedOperationIOException("TOS delete failed", exception);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("TOS delete failed", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().headObject(new HeadObjectV2Input().setBucket(profile.bucket()).setKey(objectKey));
            return true;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && (message.contains("404") || message.contains("NoSuchKey") || message.contains("NotFound"))) {
                return false;
            }
            throw StorageProviderSupport.sanitizedOperationIOException("TOS exists failed", exception);
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
        } catch (Exception ignored) {
            throw new IllegalStateException("TOS validation failed");
        } finally {
            try {
                delete(key);
            } catch (IOException exception) {
                throw new IllegalStateException("TOS validation cleanup failed");
            }
        }
    }

    @Override
    public StorageUsage usage() throws IOException {
        requireConfiguredForUsage();
        long objectCount = 0;
        long totalBytes = 0;
        Instant latest = null;
        String continuationToken = null;
        int pages = 0;
        long deadline = System.nanoTime() + MAX_USAGE_DURATION.toNanos();
        try {
            while (true) {
                if (pages >= MAX_USAGE_PAGES || System.nanoTime() >= deadline) {
                    throw new IOException("TOS storage usage aggregation exceeded safe limits");
                }
                ListObjectsType2Output output = client().listObjectsType2(new ListObjectsType2Input()
                        .setBucket(profile.bucket())
                        .setContinuationToken(continuationToken)
                        .setMaxKeys(USAGE_PAGE_SIZE));
                pages++;
                List<ListedObjectV2> contents = output.getContents();
                if (contents != null) {
                    for (ListedObjectV2 object : contents) {
                        objectCount = Math.addExact(objectCount, 1);
                        totalBytes = Math.addExact(totalBytes, object.getSize());
                        Instant modified = instantOrNull(object.getLastModified());
                        if (latest == null || (modified != null && modified.isAfter(latest))) {
                            latest = modified;
                        }
                    }
                }
                if (!output.isTruncated()) {
                    return new StorageUsage(objectCount, totalBytes, latest);
                }
                continuationToken = output.getNextContinuationToken();
                if (!hasText(continuationToken)) {
                    throw new IOException("TOS storage usage pagination failed");
                }
            }
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedUsageIOException("TOS storage usage lookup failed");
        }
    }

    private TOSV2 client() {
        requireConfigured();
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("TOS storage provider is closed");
            }
            TOSV2 current = client;
            if (current == null) {
                Credentials credentials = profile.resolveCredentials();
                requireCredentials(credentials);
                TransportConfig transport = new TransportConfig()
                        .setConnectTimeoutMills(3000)
                        .setReadTimeoutMills(5000)
                        .setWriteTimeoutMills(5000)
                        .setMaxRetryCount(2);
                current = new TOSV2ClientBuilder().build(profile.region(), profile.endpoint(),
                        credentials.accessKeyId(), credentials.accessKeySecret(), transport);
                client = current;
            }
            return current;
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("TOS storage provider is not configured");
        }
    }

    private void requireConfiguredForUsage() throws IOException {
        if (!configured()) {
            throw new IOException("TOS storage provider is not configured");
        }
    }

    private void requireCredentials(Credentials credentials) {
        if (credentials == null || !hasText(credentials.accessKeyId()) || !hasText(credentials.accessKeySecret())) {
            throw new IllegalStateException("TOS storage credentials are unavailable");
        }
    }

    private StorageObjectMetadata metadata(String objectKey, String contentType, long size,
                                           String checksum, Instant lastModified) {
        return new StorageObjectMetadata(objectKey,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                size, checksum == null || checksum.isBlank() ? "unknown" : checksum, lastModified);
    }

    private String valueOrEtag(String etag) {
        return etag == null || etag.isBlank() ? "unknown" : etag;
    }

    private Instant instant(Date date) {
        return date == null ? Instant.now() : date.toInstant();
    }

    private Instant instantOrNull(Date date) {
        return date == null ? null : date.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isMissingObject(TosServerException exception) {
        String code = exception.getCode();
        String message = exception.getMessage();
        return exception.getStatusCode() == 404 || "NoSuchKey".equalsIgnoreCase(code)
                || "NoSuchObject".equalsIgnoreCase(code)
                || (message != null && (message.contains("NoSuchKey") || message.contains("404")));
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closed = true;
        TOSV2 current = client;
        client = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
                // Shutdown must not prevent the Spring context from closing.
            }
        }
    }
}
