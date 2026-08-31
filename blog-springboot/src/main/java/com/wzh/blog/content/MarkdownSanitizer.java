package com.wzh.blog.content;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validates and normalizes Markdown before it is persisted in object storage.
 *
 * <p>Markdown is deliberately kept as Markdown. The sanitizer only removes
 * constructs that can turn into executable HTML when a renderer is too
 * permissive, and enforces the byte limit used by the content API.</p>
 */
@Component
public final class MarkdownSanitizer {

    private static final Pattern HTML_TAG = Pattern.compile("<!--(?s:.*?)-->|<[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_SCHEME = Pattern.compile(
            "(?i)(?:javascript|vbscript|data)\\s*:");
    private static final int DEFAULT_MAX_BYTES = 1_048_576;

    private final int maxBytes;

    @Autowired
    public MarkdownSanitizer() {
        this(DEFAULT_MAX_BYTES);
    }

    public MarkdownSanitizer(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Markdown max bytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    public String sanitize(String markdown) {
        Objects.requireNonNull(markdown, "markdown");
        requireUtf8Size(markdown);

        String normalized = stripUnsafeControls(markdown);
        normalized = HTML_TAG.matcher(normalized).replaceAll("");
        normalized = DANGEROUS_SCHEME.matcher(normalized).replaceAll("");
        requireUtf8Size(normalized);
        return normalized;
    }

    private void requireUtf8Size(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("Markdown content exceeds " + maxBytes + " UTF-8 bytes");
        }
    }

    private String stripUnsafeControls(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r' || character == '\t'
                    || !Character.isISOControl(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }
}
