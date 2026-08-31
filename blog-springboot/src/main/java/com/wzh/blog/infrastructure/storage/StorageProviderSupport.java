package com.wzh.blog.infrastructure.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

final class StorageProviderSupport {

    private StorageProviderSupport() {
    }

    static DigestingInputStream digesting(InputStream inputStream) {
        return new DigestingInputStream(inputStream);
    }

    static IOException asIOException(String message, Exception exception) {
        if (exception instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(message, exception);
    }

    static String checksum(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    static final class DigestingInputStream extends FilterInputStream {
        private final MessageDigest digest;
        private long count;

        private DigestingInputStream(InputStream inputStream) {
            super(inputStream);
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                digest.update((byte) value);
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                digest.update(bytes, offset, read);
                count += read;
            }
            return read;
        }

        long count() {
            return count;
        }

        String checksum() {
            return StorageProviderSupport.checksum(digest);
        }
    }

    static InputStream closeWith(InputStream content, AutoCloseable owner) {
        return new FilterInputStream(content) {
            @Override
            public void close() throws IOException {
                IOException failure = null;
                try {
                    super.close();
                } catch (IOException exception) {
                    failure = exception;
                }
                try {
                    owner.close();
                } catch (Exception exception) {
                    if (failure == null) {
                        failure = asIOException("Unable to close provider response", exception);
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
                if (failure != null) {
                    throw failure;
                }
            }
        };
    }
}
