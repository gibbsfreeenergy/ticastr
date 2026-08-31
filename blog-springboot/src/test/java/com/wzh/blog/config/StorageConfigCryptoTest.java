package com.wzh.blog.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageConfigCryptoTest {

    @Test
    void encryptsWithAUniqueNonceAndDecryptsTheOriginal() {
        StorageConfigCrypto crypto = new StorageConfigCrypto(base64Key());

        String first = crypto.encrypt("secret-value");
        String second = crypto.encrypt("secret-value");

        assertNotEquals(first, second);
        assertNotEquals("secret-value", first);
        assertEquals("secret-value", crypto.decrypt(first));
    }

    @Test
    void rejectsTamperedCiphertextWrongKeyAndMissingKey() {
        StorageConfigCrypto crypto = new StorageConfigCrypto(base64Key());
        String ciphertext = crypto.encrypt("secret-value");

        assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(ciphertext.substring(0, ciphertext.length() - 1) + "x"));
        assertThrows(IllegalStateException.class,
                () -> new StorageConfigCrypto(differentBase64Key())
                        .decrypt(ciphertext));
        assertFalse(new StorageConfigCrypto("").hasKey());
    }

    @Test
    void rejectsMalformedNonblankEncryptionKeysImmediately() {
        assertThrows(IllegalStateException.class, () -> new StorageConfigCrypto("not-base64"));
        assertThrows(IllegalStateException.class, () -> new StorageConfigCrypto(shortBase64Key()));
        assertFalse(new StorageConfigCrypto("").hasKey());
    }

    private String base64Key() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    private String differentBase64Key() {
        byte[] key = new byte[32];
        key[0] = 1;
        return Base64.getEncoder().encodeToString(key);
    }

    private String shortBase64Key() {
        return Base64.getEncoder().encodeToString(new byte[31]);
    }
}
