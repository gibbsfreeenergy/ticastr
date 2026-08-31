package com.wzh.blog.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void propagatesSafeRequestIdToMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationContext.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(chain.toString()).thenReturn("chain");

        var captured = new Object[] { null };
        org.mockito.Mockito.doAnswer(invocation -> {
            captured[0] = org.slf4j.MDC.get(CorrelationContext.TRACE_ID_MDC_KEY);
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("request-123");
        assertThat(response.getHeader(CorrelationContext.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(org.slf4j.MDC.get(CorrelationContext.TRACE_ID_MDC_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationContext.REQUEST_ID_HEADER, "bad\r\nX-Injected: true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (request1, response1) -> {
            assertThat(org.slf4j.MDC.get(CorrelationContext.REQUEST_ID_MDC_KEY)).isNotBlank();
        });

        String responseId = response.getHeader(CorrelationContext.REQUEST_ID_HEADER);
        assertThat(responseId).isNotEqualTo("bad\r\nX-Injected: true");
        assertThat(CorrelationContext.isSafe(responseId)).isTrue();
    }
}
