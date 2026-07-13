package com.wzh.blog.handler;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringTokenFilterTest {

    @Test
    void hidesMetricsWhenTheTokenIsMissing() throws Exception {
        MonitoringTokenFilter filter = new MonitoringTokenFilter("monitoring-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(continued).isFalse();
    }

    @Test
    void permitsMetricsWhenTheTokenMatches() throws Exception {
        MonitoringTokenFilter filter = new MonitoringTokenFilter("monitoring-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("X-Monitoring-Token", "monitoring-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(continued).isTrue();
    }
}
