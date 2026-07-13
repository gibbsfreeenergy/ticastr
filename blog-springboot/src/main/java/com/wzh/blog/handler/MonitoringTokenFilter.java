package com.wzh.blog.handler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Protects metrics scraping without exposing the endpoint through user sessions. */
@Component
public class MonitoringTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-Monitoring-Token";
    private final String monitoringToken;

    public MonitoringTokenFilter(@Value("${monitoring.token:}") String monitoringToken) {
        this.monitoringToken = monitoringToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/actuator/prometheus".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presentedToken = request.getHeader(TOKEN_HEADER);
        if (monitoringToken.isBlank() || presentedToken == null
                || !MessageDigest.isEqual(monitoringToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.flushBuffer();
            return;
        }
        filterChain.doFilter(request, response);
    }
}
