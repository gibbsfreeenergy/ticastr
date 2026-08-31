package com.wzh.blog.infrastructure.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderConfigSnapshot;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.media.StorageUsage;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CosStorageProvider implements StorageProvider {

    private static final int USAGE_PAGE_SIZE = 1000;
    private static final int MAX_USAGE_PAGES = 10_000;
    private static final Duration MAX_USAGE_DURATION = Duration.ofSeconds(30);

    private final StorageProviderConfigSnapshot profile;
    private volatile COSClient client;

    public CosStorageProvider(StorageProviderConfigSnapshot profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        if (profile.provider() != StorageProviderType.COS) {
            throw new IllegalArgumentException("Storage profile is not a COS profile");
        }
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.COS;
    }

    @Override
    public boolean configured() {
        return hasText(profile.endpoint()) && hasText(profile.bucket()) && hasText(profile.region())
                && hasText(profile.accessKeyId()) && hasText(profile.accessKeySecret());
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
            client().putObject(profile.bucket(), objectKey, digesting, metadata);
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
            COSObject object = client().getObject(profile.bucket(), objectKey);
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
            ObjectMetadata metadata = client().getObjectMetadata(profile.bucket(), objectKey);
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
            client().deleteObject(profile.bucket(), objectKey);
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
            return client().doesObjectExist(profile.bucket(), objectKey);
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
                    throw new IOException("COS storage usage aggregation exceeded safe limits");
                }
                ListObjectsRequest request = new ListObjectsRequest(profile.bucket(), null, marker, null, USAGE_PAGE_SIZE);
                ObjectListing listing = client().listObjects(request);
                pages++;
                List<COSObjectSummary> summaries = listing.getObjectSummaries();
                if (summaries != null) {
                    for (COSObjectSummary summary : summaries) {
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
                    throw new IOException("COS storage usage pagination failed");
                }
            }
        } catch (RuntimeException exception) {
            throw StorageProviderSupport.sanitizedUsageIOException("COS storage usage lookup failed");
        }
    }

    private COSClient client() {
        requireConfigured();
        COSClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    COSCredentials credentials = new BasicCOSCredentials(profile.accessKeyId(), profile.accessKeySecret());
                    ClientConfig configuration = new ClientConfig(new Region(profile.region()));
                    configuration.setConnectionTimeout(3000);
                    configuration.setSocketTimeout(5000);
                    configuration.setMaxErrorRetry(2);
                    configureEndpoint(configuration);
                    current = new COSClient(credentials, configuration);
                    client = current;
                }
            }
        }
        return current;
    }

    private void configureEndpoint(ClientConfig configuration) {
        URI endpoint = URI.create(profile.endpoint().contains("://") ? profile.endpoint() : "https://" + profile.endpoint());
        if (!hasText(endpoint.getHost())) {
            throw new IllegalArgumentException("COS storage endpoint is invalid");
        }
        configuration.setEndPointSuffix(endpoint.getHost() + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort()));
        if ("http".equalsIgnoreCase(endpoint.getScheme())) {
            configuration.setHttpProtocol(HttpProtocol.http);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("COS storage provider is not configured");
        }
    }

    private void requireConfiguredForUsage() throws IOException {
        if (!configured()) {
            throw new IOException("COS storage provider is not configured");
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

    private boolean isMissingObject(CosServiceException exception) {
        String code = exception.getErrorCode();
        String message = exception.getMessage();
        return exception.getStatusCode() == 404 || "NoSuchKey".equalsIgnoreCase(code)
                || "NoSuchObject".equalsIgnoreCase(code)
                || (message != null && (message.contains("NoSuchKey") || message.contains("404")));
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        COSClient current = client;
        client = null;
        if (current != null) {
            current.shutdown();
        }
    }
}
