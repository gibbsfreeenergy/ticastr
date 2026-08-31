package com.wzh.blog.infrastructure.storage;

import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageProviderTest {

    @TempDir
    Path root;

    @Test
    void writesReadsHeadsAndDeletesStreamingObject() throws Exception {
        LocalStorageProvider provider = new LocalStorageProvider(root);
        byte[] body = "## hello\n\ncontent".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        StorageObjectMetadata stored = provider.put(
                "articles/1/1-asset.md",
                new ByteArrayInputStream(body),
                body.length,
                "text/markdown; charset=utf-8");

        assertEquals(body.length, stored.sizeBytes());
        assertEquals("text/markdown; charset=utf-8", stored.contentType());
        assertEquals("4b545dc9e550703db1141ab7ab0b6706ccee8204e5512f5c6b24c83896ea8f0a", stored.checksum());
        assertTrue(provider.exists(stored.objectKey()));
        assertEquals(stored.checksum(), provider.head(stored.objectKey()).checksum());

        try (StorageObject object = provider.get(stored.objectKey())) {
            assertArrayEquals(body, object.content().readAllBytes());
        }

        provider.delete(stored.objectKey());
        assertFalse(provider.exists(stored.objectKey()));
        assertThrows(IOException.class, () -> provider.head(stored.objectKey()));
    }

    @Test
    void closesInputStreamReturnedByProviderAndRejectsPathEscape() throws Exception {
        LocalStorageProvider provider = new LocalStorageProvider(root);
        byte[] body = "body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        provider.put("media/asset.txt", new ByteArrayInputStream(body), body.length, "text/plain");

        TrackingInputStream source = new TrackingInputStream(body);
        provider.put("media/second.txt", source, body.length, "text/plain");
        assertTrue(source.closed);

        assertThrows(IllegalArgumentException.class,
                () -> provider.get("../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> provider.put("media\\outside", new ByteArrayInputStream(body), body.length, "text/plain"));
    }

    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed;

        private TrackingInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
