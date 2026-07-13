package com.wzh.blog.util;

import com.alibaba.fastjson2.JSON;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Produces bounded audit JSON without credentials or one-time codes. */
public class AuditLogSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "accesstoken", "token",
            "secret", "appsecret", "code", "clientid");

    private final int maxLength;

    public AuditLogSanitizer(int maxLength) {
        this.maxLength = maxLength;
    }

    public String sanitize(Object value) {
        try {
            Object tree = JSON.parse(JSON.toJSONString(value));
            redact(tree);
            return truncate(JSON.toJSONString(tree));
        } catch (RuntimeException exception) {
            return "[unserializable]";
        }
    }

    @SuppressWarnings("unchecked")
    private void redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).replaceAll((key, child) -> {
                if (key != null && SENSITIVE_KEYS.contains(key.toString().toLowerCase(Locale.ROOT))) {
                    return "***";
                }
                redact(child);
                return child;
            });
        } else if (value instanceof List<?> list) {
            list.forEach(this::redact);
        }
    }

    private String truncate(String value) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 14)) + "...[truncated]";
    }
}
