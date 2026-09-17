package com.wzh.blog.util;

import jakarta.servlet.http.HttpServletRequest;

/** Extracts the client address used to key the login rate limiter. */
public final class IpUtils {

    private IpUtils() {
    }

    public static String getIpAddress(HttpServletRequest request) {
        String address = firstPresent(
                request.getHeader("X-Real-IP"),
                request.getHeader("Proxy-Client-IP"),
                request.getHeader("WL-Proxy-Client-IP"),
                request.getRemoteAddr());
        if (address != null && address.length() > 15 && address.contains(",")) {
            return address.substring(0, address.indexOf(','));
        }
        return address == null ? "" : address;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }
}
