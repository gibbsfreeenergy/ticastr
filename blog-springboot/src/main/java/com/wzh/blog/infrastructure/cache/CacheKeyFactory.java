package com.wzh.blog.infrastructure.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Centralizes cache key namespaces and prevents raw request text in keys. */
public class CacheKeyFactory {

    public String websiteConfig() {
        return "ticastr:website:config:v1";
    }

    public String about() {
        return "ticastr:website:about:v1";
    }

    public String pageCover() {
        return "ticastr:page:cover:v1";
    }

    public String articleMetadata(Integer articleId, Object version) {
        return "ticastr:article:metadata:v1:" + articleId + ":" + version;
    }

    public String searchResult(String normalizedQuery) {
        return "ticastr:search:result:v1:" + digest(normalizedQuery);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
