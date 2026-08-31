package com.wzh.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class StorageConfigCrypto {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_SIZE_BYTES = 12;
    private static final int KEY_SIZE_BYTES = 32;
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final String ERROR_MESSAGE = "Storage configuration encryption is unavailable";
    private static final String DECRYPT_ERROR_MESSAGE = "Unable to decrypt storage configuration";

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public StorageConfigCrypto(@Value("${storage.config-encryption-key:}") String base64Key) {
        this.key = decodeKey(base64Key);
    }

    public boolean hasKey() {
        return key != null;
    }

    public String encrypt(String plainText) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_SIZE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return VERSION + ":" + Base64.getEncoder().encodeToString(nonce)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(ERROR_MESSAGE, exception);
        }
    }

    public String decrypt(String encryptedValue) {
        requireKey();
        try {
            String[] parts = encryptedValue.split(":", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported storage configuration version");
            }

            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            if (nonce.length != NONCE_SIZE_BYTES) {
                throw new IllegalArgumentException("Invalid storage configuration nonce");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (RuntimeException | GeneralSecurityException exception) {
            throw new IllegalStateException(DECRYPT_ERROR_MESSAGE, exception);
        }
    }

    private SecretKey decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != KEY_SIZE_BYTES) {
                throw new IllegalStateException(ERROR_MESSAGE);
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(ERROR_MESSAGE, exception);
        }
    }

    private void requireKey() {
        if (!hasKey()) {
            throw new IllegalStateException(ERROR_MESSAGE);
        }
    }
}
