package com.wzh.blog.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Normalizes anonymous chat identities without persisting a browser-visible
 * identifier in the database.
 */
public final class ChatIdentityUtils {

    private ChatIdentityUtils() {
    }

    public static boolean isValidClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(clientId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String hashClientId(String clientId) {
        if (!isValidClientId(clientId)) {
            throw new IllegalArgumentException("Client identity must be a UUID");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(clientId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
