package com.wzh.blog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HTMLUtilsTest {

    @Test
    void removesExecutableMarkupAndProtocols() {
        String sanitized = HTMLUtils.sanitizeRichText("""
                <p>Hello</p><script>alert('xss')</script>
                <img src=\"https://cdn.example.test/image.png\" onerror=\"alert(1)\">
                <a href=\"javascript:alert(1)\">unsafe</a>
                """);

        assertThat(sanitized).contains("<p>Hello</p>");
        assertThat(sanitized).contains("https://cdn.example.test/image.png");
        assertThat(sanitized).doesNotContainIgnoringCase("script", "onerror", "javascript:");
    }

    @Test
    void stripsAllMarkupFromPlainText() {
        assertThat(HTMLUtils.sanitizePlainText("<strong>Title</strong><img src=\"https://example.test/a.png\">"))
                .isEqualTo("Title");
    }
}
