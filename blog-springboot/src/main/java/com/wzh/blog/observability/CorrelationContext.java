package com.wzh.blog.observability;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Request/task correlation state shared by HTTP and asynchronous adapters. */
public final class CorrelationContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private CorrelationContext() {
    }

    public static String currentId() {
        String current = MDC.get(TRACE_ID_MDC_KEY);
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }

    public static String resolve(String requestId, String correlationId) {
        if (isSafe(requestId)) {
            return requestId;
        }
        if (isSafe(correlationId)) {
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }

    public static Scope open(String id) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String resolved = isSafe(id) ? id : UUID.randomUUID().toString();
        MDC.put(REQUEST_ID_MDC_KEY, resolved);
        MDC.put(TRACE_ID_MDC_KEY, resolved);
        return () -> {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        };
    }

    public static boolean isSafe(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
