package com.wzh.blog.infrastructure.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.wzh.blog.media.StorageProviderConfigSnapshot;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.media.StorageUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageUsageContractTest {

    @Test
    void aggregatesAllObjectsFromFakePaginatedOssSummaries() throws Exception {
        OSS client = mock(OSS.class);
        ObjectListing first = listing(true, "page-2",
                summary(3, "2026-09-01T00:00:00Z"),
                summary(5, "2026-09-01T02:00:00Z"));
        ObjectListing second = listing(false, null,
                summary(7, "2026-09-01T01:00:00Z"));
        AtomicInteger page = new AtomicInteger();
        when(client.listObjects(any(ListObjectsRequest.class)))
                .thenAnswer(invocation -> page.getAndIncrement() == 0 ? first : second);
        OssStorageProvider provider = new OssStorageProvider(snapshot(), () -> client);

        StorageUsage usage = provider.usage();

        assertEquals(3, usage.objectCount());
        assertEquals(15, usage.totalBytes());
        assertEquals(Instant.parse("2026-09-01T02:00:00Z"), usage.latestObjectModified());
        ArgumentCaptor<ListObjectsRequest> requests = ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(client, org.mockito.Mockito.times(2)).listObjects(requests.capture());
        assertEquals(1000, requests.getAllValues().get(0).getMaxKeys());
        assertEquals(null, requests.getAllValues().get(0).getMarker());
        assertEquals("page-2", requests.getAllValues().get(1).getMarker());
    }

    @Test
    void reportsAnIoFailureForAnUnconfiguredCloudUsageRequest() {
        StorageProviderConfigSnapshot incomplete = new StorageProviderConfigSnapshot(8L, "incomplete-oss",
                StorageProviderType.OSS, "https://oss.example.test", "blog-assets", "cn-shanghai", null,
                "https://cdn.example.test", "access-id", null);

        assertThrows(IOException.class, () -> new OssStorageProvider(incomplete).usage());
    }

    @Test
    void removesSdkFailureDetailsFromCloudUsageErrors() {
        OSS client = mock(OSS.class);
        when(client.listObjects(any(ListObjectsRequest.class)))
                .thenThrow(new IllegalStateException("https://storage.example.test?Authorization=secret-signature"));

        IOException failure = assertThrows(IOException.class,
                () -> new OssStorageProvider(snapshot(), () -> client).usage());

        assertEquals("OSS storage usage lookup failed", failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("secret-signature"));
    }

    @Test
    void removesSdkFailureDetailsFromOrdinaryCloudOperationErrors() {
        OSS client = mock(OSS.class);
        when(client.getObjectMetadata(anyString(), anyString()))
                .thenThrow(new IllegalStateException("https://storage.example.test?token=secret-token"));

        IOException failure = assertThrows(IOException.class,
                () -> new OssStorageProvider(snapshot(), () -> client).head("asset.txt"));

        assertEquals("OSS head failed", failure.getMessage());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("secret-token"));
    }

    private static StorageProviderConfigSnapshot snapshot() {
        return new StorageProviderConfigSnapshot(7L, "oss-profile", StorageProviderType.OSS,
                "https://oss.example.test", "blog-assets", "cn-shanghai", null,
                "https://cdn.example.test", "access-id", "access-secret");
    }

    private static ObjectListing listing(boolean truncated, String marker, OSSObjectSummary... summaries) {
        ObjectListing listing = new ObjectListing();
        listing.setObjectSummaries(List.of(summaries));
        listing.setTruncated(truncated);
        listing.setNextMarker(marker);
        return listing;
    }

    private static OSSObjectSummary summary(long size, String lastModified) {
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setSize(size);
        summary.setLastModified(Date.from(Instant.parse(lastModified)));
        return summary;
    }
}
