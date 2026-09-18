package com.wzh.blog.vo.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficMonitorVOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsBridgeNamesButKeepsAdminResponseNamesStable() throws Exception {
        TrafficMonitorVO.Overview overview = objectMapper.readValue(
                "{\"total_conns\":7,\"total_ips\":2,\"today_conns\":3,"
                        + "\"total_domains\":4,\"first_conn\":\"2026-09-18 10:00:00\","
                        + "\"last_conn\":\"2026-09-18 11:00:00\",\"alerts_open\":1,"
                        + "\"traffic_up\":100,\"traffic_down\":200,\"online\":2,"
                        + "\"xray\":\"active\",\"collector\":{\"at\":1,\"lag\":2},"
                        + "\"timezone\":\"Asia/Shanghai\",\"generated_at\":3}",
                TrafficMonitorVO.Overview.class);

        assertThat(overview.totalConnections()).isEqualTo(7);
        assertThat(overview.totalIps()).isEqualTo(2);
        assertThat(objectMapper.writeValueAsString(overview))
                .contains("\"totalConnections\":7")
                .doesNotContain("total_conns");
    }
}
