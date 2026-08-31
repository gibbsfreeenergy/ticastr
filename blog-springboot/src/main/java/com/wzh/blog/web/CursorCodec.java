package com.wzh.blog.web;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signs opaque cursor positions so clients cannot skip or alter feed order. */
@Component
public class CursorCodec {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;
    private final Duration lifetime;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CursorCodec(@Value("${app.pagination.cursor-secret:local-dev-cursor-secret-change-me}") String secret,
                       @Value("${app.pagination.cursor-lifetime-seconds:900}") long lifetimeSeconds) {
        this(secret, Duration.ofSeconds(lifetimeSeconds));
    }

    public CursorCodec(String secret, Duration lifetime) {
        this(secret, lifetime, Clock.systemUTC());
    }

    public CursorCodec(String secret, Duration lifetime, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("cursor secret must not be blank");
        }
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("cursor lifetime must be positive");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.lifetime = lifetime;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String fingerprint(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String encode(LocalDateTime createTime, int id, String filterFingerprint) {
        if (createTime == null || id < 1 || filterFingerprint == null || filterFingerprint.isBlank()) {
            throw new IllegalArgumentException("cursor position is incomplete");
        }
        long expiresAt = Instant.now(clock).plus(lifetime).getEpochSecond();
        String payload = createTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                + "." + id + "." + filterFingerprint + "." + expiresAt;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "." + signature(payload)).getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encoded, String expectedFingerprint) {
        try {
            if (encoded == null || encoded.isBlank() || expectedFingerprint == null || expectedFingerprint.isBlank()) {
                throw new IllegalArgumentException("cursor is invalid");
            }
            String decoded = decodeCanonical(encoded);
            String[] parts = decoded.split("\\.", -1);
            if (parts.length != 5) {
                throw new IllegalArgumentException("cursor is invalid");
            }
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(parts[4].getBytes(StandardCharsets.UTF_8),
                    signature(payload).getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("cursor is invalid");
            }
            long expiresAt = Long.parseLong(parts[3]);
            if (expiresAt <= Instant.now(clock).getEpochSecond()) {
                throw new IllegalArgumentException("cursor has expired");
            }
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                    expectedFingerprint.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("cursor does not match this feed");
            }
            long epochMillis = Long.parseLong(parts[0]);
            int id = Integer.parseInt(parts[1]);
            if (id < 1) {
                throw new IllegalArgumentException("cursor is invalid");
            }
            return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC), id);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }

    public String encodeVersion(int version, String filterFingerprint) {
        if (version < 1 || filterFingerprint == null || filterFingerprint.isBlank()) {
            throw new IllegalArgumentException("version cursor is incomplete");
        }
        long expiresAt = Instant.now(clock).plus(lifetime).getEpochSecond();
        String payload = "v." + version + "." + filterFingerprint + "." + expiresAt;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "." + signature(payload)).getBytes(StandardCharsets.UTF_8));
    }

    public int decodeVersion(String encoded, String expectedFingerprint) {
        try {
            if (encoded == null || encoded.isBlank() || expectedFingerprint == null || expectedFingerprint.isBlank()) {
                throw new IllegalArgumentException("version cursor is invalid");
            }
            String decoded = decodeCanonical(encoded);
            String[] parts = decoded.split("\\.", -1);
            if (parts.length != 5 || !"v".equals(parts[0])) {
                throw new IllegalArgumentException("version cursor is invalid");
            }
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(parts[4].getBytes(StandardCharsets.UTF_8),
                    signature(payload).getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("version cursor is invalid");
            }
            if (Long.parseLong(parts[3]) <= Instant.now(clock).getEpochSecond()) {
                throw new IllegalArgumentException("version cursor has expired");
            }
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                    expectedFingerprint.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("version cursor does not match this article");
            }
            int version = Integer.parseInt(parts[1]);
            if (version < 1) {
                throw new IllegalArgumentException("version cursor is invalid");
            }
            return version;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("version cursor is invalid", exception);
        }
    }

    public String encodeOffset(int offset, String filterFingerprint) {
        if (offset < 0 || filterFingerprint == null || filterFingerprint.isBlank()) {
            throw new IllegalArgumentException("offset cursor is incomplete");
        }
        long expiresAt = Instant.now(clock).plus(lifetime).getEpochSecond();
        String payload = "o." + offset + "." + filterFingerprint + "." + expiresAt;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload + "." + signature(payload)).getBytes(StandardCharsets.UTF_8));
    }

    public int decodeOffset(String encoded, String expectedFingerprint) {
        try {
            if (encoded == null || encoded.isBlank() || expectedFingerprint == null || expectedFingerprint.isBlank()) {
                throw new IllegalArgumentException("offset cursor is invalid");
            }
            String decoded = decodeCanonical(encoded);
            String[] parts = decoded.split("\\.", -1);
            if (parts.length != 5 || !"o".equals(parts[0])) {
                throw new IllegalArgumentException("offset cursor is invalid");
            }
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(parts[4].getBytes(StandardCharsets.UTF_8),
                    signature(payload).getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("offset cursor is invalid");
            }
            if (Long.parseLong(parts[3]) <= Instant.now(clock).getEpochSecond()) {
                throw new IllegalArgumentException("offset cursor has expired");
            }
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                    expectedFingerprint.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("offset cursor does not match this query");
            }
            int offset = Integer.parseInt(parts[1]);
            if (offset < 0) {
                throw new IllegalArgumentException("offset cursor is invalid");
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("offset cursor is invalid", exception);
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign cursor", exception);
        }
    }

    private String decodeCanonical(String encoded) {
        validatePaddingBits(encoded);
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (!canonical.equals(encoded)) {
            throw new IllegalArgumentException("cursor is not canonical");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void validatePaddingBits(String encoded) {
        int remainder = encoded.length() % 4;
        if (remainder == 1) {
            throw new IllegalArgumentException("cursor has invalid base64 length");
        }
        if (remainder == 0) {
            return;
        }
        char last = encoded.charAt(encoded.length() - 1);
        int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".indexOf(last);
        if (value < 0) {
            throw new IllegalArgumentException("cursor has invalid base64 character");
        }
        int mask = remainder == 2 ? 0x0f : 0x03;
        if ((value & mask) != 0) {
            throw new IllegalArgumentException("cursor has non-zero base64 padding bits");
        }
    }

    public record Cursor(LocalDateTime createTime, int id) {
    }
}
