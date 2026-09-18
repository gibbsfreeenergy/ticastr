package com.wzh.blog.infrastructure.traffic;

import com.wzh.blog.config.XrayTrafficProperties;
import com.wzh.blog.vo.traffic.TrafficControlRequest;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class XrayTrafficClientTest {

    @Test
    void acceptsBridgeAcknowledgementResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://bridge.test/v1/alerts/ack"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"ok\":true,\"mode\":\"ack\",\"runtime_applied\":false,\"updated\":1}",
                        MediaType.APPLICATION_JSON));

        XrayTrafficProperties properties = new XrayTrafficProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://bridge.test");
        properties.setSharedSecret("test-secret");

        TrafficMonitorVO.ControlResult result = new XrayTrafficClient(restTemplate, properties)
                .acknowledge(new TrafficControlRequest.AlertAckRequest(1L, false));

        assertThat(result.ok()).isTrue();
        server.verify();
    }
}
