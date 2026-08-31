package com.wzh.blog.infrastructure.storage;

import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Filesystem implementation used for local development and single-instance
 * deployments. The root is resolved once and every operation remains inside
 * it after normalization.
 */
public final class LocalStorageProvider implements StorageProvider {

    private static final int BUFFER_SIZE = 16 * 1024;
    private final Path root;

    public LocalStorageProvider(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Local storage root must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create local storage root", exception);
        }
    }

    @Override
    public StorageProviderType type() {
        return StorageProviderType.LOCAL;
    }

    @Override
    public StorageObjectMetadata put(String objectKey, InputStream content, long size, String contentType)
            throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        if (content == null) {
            throw new IllegalArgumentException("Content stream must not be null");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Content size must not be negative");
        }
        Path target = resolve(objectKey);
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".upload-", ".tmp");
        boolean moved = false;
        try {
            MessageDigest digest = sha256();
            long copied = 0;
            try (InputStream input = new BufferedInputStream(content);
                 var digestInput = new java.security.DigestInputStream(input, digest);
                 var output = new BufferedOutputStream(Files.newOutputStream(
                         temporary, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = digestInput.read(buffer)) != -1) {
                    copied += read;
                    if (copied > size) {
                        throw new IOException("Object stream is larger than declared size");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (copied != size) {
                throw new IOException("Object stream size does not match declared size");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            } catch (FileAlreadyExistsException exception) {
                throw new IOException("Object key already exists", exception);
            }
            moved = true;
            return metadata(target, objectKey, contentType, HexFormat.of().formatHex(digest.digest()));
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override
    public StorageObject get(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new NoSuchFileException(objectKey);
        }
        StorageObjectMetadata metadata = head(objectKey);
        return new StorageObject(Files.newInputStream(target, StandardOpenOption.READ), metadata);
    }

    @Override
    public StorageObjectMetadata head(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new NoSuchFileException(objectKey);
        }
        return metadata(target, objectKey, detectContentType(target), checksum(target));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public boolean exists(String objectKey) throws IOException {
        ObjectKeyPolicy.requireSafe(objectKey);
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public void validateConnection() {
        String key = ".health/" + UUID.randomUUID() + ".txt";
        byte[] body = "storage-health".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            put(key, new java.io.ByteArrayInputStream(body), body.length, "text/plain");
            try (StorageObject object = get(key)) {
                if (!java.util.Arrays.equals(body, object.content().readAllBytes())) {
                    throw new IOException("Local storage read verification failed");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Local storage validation failed", exception);
        } finally {
            try {
                delete(key);
            } catch (IOException exception) {
                throw new IllegalStateException("Local storage cleanup validation failed", exception);
            }
        }
    }

    public Path root() {
        return root;
    }

    private Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Unsafe object key");
        }
        return resolved;
    }

    private StorageObjectMetadata metadata(Path path, String objectKey, String contentType, String checksum)
            throws IOException {
        return new StorageObjectMetadata(
                objectKey,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                Files.size(path),
                checksum,
                Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()));
    }

    private String detectContentType(Path path) throws IOException {
        String contentType = Files.probeContentType(path);
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private String checksum(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
