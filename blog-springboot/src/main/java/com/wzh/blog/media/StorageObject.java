package com.wzh.blog.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider-neutral streaming object. Closing it always closes the underlying
 * SDK response stream.
 */
public final class StorageObject implements AutoCloseable {

    private final InputStream content;
    private final StorageObjectMetadata metadata;
    private final AtomicBoolean closed = new AtomicBoolean();

    public StorageObject(InputStream content, StorageObjectMetadata metadata) {
        this.content = Objects.requireNonNull(content, "content");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public InputStream content() {
        if (closed.get()) {
            throw new IllegalStateException("Storage object is already closed");
        }
        return content;
    }

    public StorageObjectMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            content.close();
        }
    }
}
