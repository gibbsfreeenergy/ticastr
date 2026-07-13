package com.wzh.blog.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogSanitizerTest {

    @Test
    void redactsNestedCredentials() {
        AuditLogSanitizer sanitizer = new AuditLogSanitizer(1000);

        String result = sanitizer.sanitize(Map.of(
                "username", "person@example.com",
                "password", "secret-value",
                "nested", Map.of("accessToken", "oauth-token", "code", "123456")));

        assertThat(result).contains("person@example.com").doesNotContain("secret-value", "oauth-token", "123456");
        assertThat(result).contains("***");
    }

    @Test
    void truncatesLargePayloads() {
        AuditLogSanitizer sanitizer = new AuditLogSanitizer(40);

        assertThat(sanitizer.sanitize(Map.of("content", "x".repeat(100))))
                .hasSize(40)
                .endsWith("...[truncated]");
    }
}
