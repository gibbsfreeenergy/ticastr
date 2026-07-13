package com.wzh.blog.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatIdentityUtilsTest {

    private static final String CLIENT_ID = "a0f8a5a5-7b2a-4b56-8b14-1058b45ad9b3";

    @Test
    void acceptsUuidAndReturnsStableNonReversibleIdentifier() {
        String hash = ChatIdentityUtils.hashClientId(CLIENT_ID);

        assertTrue(ChatIdentityUtils.isValidClientId(CLIENT_ID));
        assertEquals(hash, ChatIdentityUtils.hashClientId(CLIENT_ID));
        assertEquals(64, hash.length());
    }

    @Test
    void rejectsMalformedClientIdentity() {
        assertFalse(ChatIdentityUtils.isValidClientId("not-a-uuid"));
        assertThrows(IllegalArgumentException.class,
                () -> ChatIdentityUtils.hashClientId("not-a-uuid"));
    }
}
