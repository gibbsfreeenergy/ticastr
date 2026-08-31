package com.wzh.blog.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Adds a safe correlation id to every HTTP request and response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationContext.resolve(
                request.getHeader(CorrelationContext.REQUEST_ID_HEADER),
                request.getHeader(CorrelationContext.CORRELATION_ID_HEADER));
        response.setHeader(CorrelationContext.REQUEST_ID_HEADER, correlationId);
        try (CorrelationContext.Scope ignored = CorrelationContext.open(correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
