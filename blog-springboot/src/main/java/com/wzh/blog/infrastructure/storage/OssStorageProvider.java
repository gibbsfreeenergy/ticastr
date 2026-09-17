package com.wzh.blog.infrastructure.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.ServiceException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
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
import java.util.function.Supplier;

public final class OssStorageProvider implements StorageProvider {

    private static final int USAGE_PAGE_SIZE = 1000;
    private static final int MAX_USAGE_PAGES = 10_000;
    private static final Duration MAX_USAGE_DURATION = Duration.ofSeconds(30);

    private final StorageProviderConfigSnapshot profile;
    private final CredentialAwareClientFactory clientFactory;
    private volatile OSS client;
    private boolean closed;

    public OssStorageProvider(StorageProviderConfigSnapshot profile) {
        this(profile, null);
    }

    OssStorageProvider(StorageProviderConfigSnapshot profile, Supplier<OSS> clientFactory) {
        this(profile, clientFactory == null ? null : ignored -> clientFactory.get(), true);
    }

    OssStorageProvider(StorageProviderConfigSnapshot profile,
                       CredentialAwareClientFactory clientFactory,
                       boolean testSeam) {
        this.profile = Objects.requireNonNull(profile, "profile");
        if (profile.provider() != StorageProviderType.OSS) {
            throw new IllegalArgumentException("Storage profile is not an OSS profile");
        }
        this.clientFactory = clientFactory == null ? this::buildClient : clientFactory;
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.OSS;
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
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        metadata.setContentType(contentType);
        metadata.setObjectAcl(CannedAccessControlList.PublicRead);
        try {
            client().putObject(new PutObjectRequest(profile.bucket(), objectKey, digesting, metadata));
            if (digesting.count() != size) {
                delete(objectKey);
                throw new IOException("Object stream size does not match declared size");
            }
            return metadata(objectKey, contentType, size, digesting.checksum(), Instant.now());
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("OSS upload failed", exception);
        }
    }

    @Override
    public StorageObject get(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            OSSObject object = client().getObject(profile.bucket(), objectKey);
            ObjectMetadata metadata = object.getObjectMetadata();
            return new StorageObject(
                    StorageProviderSupport.closeWith(object.getObjectContent(), object),
                    metadata(objectKey, metadata.getContentType(), metadata.getContentLength(),
                            valueOrEtag(metadata.getETag()), instant(metadata.getLastModified())));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("OSS read failed", exception);
        }
    }

    @Override
    public StorageObjectMetadata head(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            ObjectMetadata metadata = client().getObjectMetadata(profile.bucket(), objectKey);
            return metadata(objectKey, metadata.getContentType(), metadata.getContentLength(),
                    valueOrEtag(metadata.getETag()), instant(metadata.getLastModified()));
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("OSS head failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            client().deleteObject(profile.bucket(), objectKey);
        } catch (ServiceException exception) {
            if (isMissingObject(exception)) {
                return;
            }
            throw StorageProviderSupport.sanitizedOperationIOException("OSS delete failed", exception);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("OSS delete failed", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        try {
            return client().doesObjectExist(profile.bucket(), objectKey);
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedOperationIOException("OSS exists failed", exception);
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
                    throw new IOException("OSS read verification failed");
                }
            }
        } catch (Exception ignored) {
            throw new IllegalStateException("OSS validation failed");
        } finally {
            try {
                delete(key);
            } catch (IOException exception) {
                throw new IllegalStateException("OSS validation cleanup failed");
            }
        }
    }

    @Override
    public StorageUsage usage() throws IOException {
        requireConfiguredForUsage();
        long objectCount = 0;
        long totalBytes = 0;
        Instant latest = null;
        String marker = null;
        int pages = 0;
        long deadline = System.nanoTime() + MAX_USAGE_DURATION.toNanos();
        try {
            while (true) {
                if (pages >= MAX_USAGE_PAGES || System.nanoTime() >= deadline) {
                    throw new IOException("OSS storage usage aggregation exceeded safe limits");
                }
                ListObjectsRequest request = new ListObjectsRequest(profile.bucket());
                request.setMaxKeys(USAGE_PAGE_SIZE);
                request.setMarker(marker);
                ObjectListing listing = client().listObjects(request);
                pages++;
                List<OSSObjectSummary> summaries = listing.getObjectSummaries();
                if (summaries != null) {
                    for (OSSObjectSummary summary : summaries) {
                        objectCount = Math.addExact(objectCount, 1);
                        totalBytes = Math.addExact(totalBytes, summary.getSize());
                        Instant modified = instantOrNull(summary.getLastModified());
                        if (latest == null || (modified != null && modified.isAfter(latest))) {
                            latest = modified;
                        }
                    }
                }
                if (!listing.isTruncated()) {
                    return new StorageUsage(objectCount, totalBytes, latest);
                }
                marker = listing.getNextMarker();
                if (!hasText(marker)) {
                    throw new IOException("OSS storage usage pagination failed");
                }
            }
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedUsageIOException("OSS storage usage lookup failed");
        }
    }

    private OSS client() {
        requireConfigured();
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("OSS storage provider is closed");
            }
            OSS current = client;
            if (current == null) {
                Credentials credentials = profile.resolveCredentials();
                current = Objects.requireNonNull(clientFactory.create(credentials),
                        "OSS client factory returned null");
                client = current;
            }
            return current;
        }
    }

    private OSS buildClient(Credentials credentials) {
        requireCredentials(credentials);
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(3000);
        configuration.setSocketTimeout(5000);
        configuration.setMaxErrorRetry(2);
        return new OSSClientBuilder().build(profile.endpoint(), credentials.accessKeyId(),
                credentials.accessKeySecret(), configuration);
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("OSS storage provider is not configured");
        }
    }

    private void requireConfiguredForUsage() throws IOException {
        if (!configured()) {
            throw new IOException("OSS storage provider is not configured");
        }
    }

    private void requireCredentials(Credentials credentials) {
        if (credentials == null || !hasText(credentials.accessKeyId()) || !hasText(credentials.accessKeySecret())) {
            throw new IllegalStateException("OSS storage credentials are unavailable");
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

    private boolean isMissingObject(ServiceException exception) {
        String code = exception.getErrorCode();
        String message = exception.getMessage();
        return "NoSuchKey".equalsIgnoreCase(code) || "NoSuchObject".equalsIgnoreCase(code)
                || (message != null && (message.contains("NoSuchKey") || message.contains("404")));
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closed = true;
        OSS current = client;
        client = null;
        if (current != null) {
            try {
                current.shutdown();
            } catch (RuntimeException ignored) {
                // Shutdown is best effort and must remain idempotent.
            }
        }
    }

    @FunctionalInterface
    interface CredentialAwareClientFactory {
        OSS create(Credentials credentials);
    }
}
