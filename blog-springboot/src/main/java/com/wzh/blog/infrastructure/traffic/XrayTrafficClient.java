package com.wzh.blog.infrastructure.traffic;

import com.wzh.blog.config.XrayTrafficProperties;
import com.wzh.blog.exception.XrayTrafficUnavailableException;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed HTTP client for the DMIT read-only traffic bridge. */
@Component
public class XrayTrafficClient {

    private static final Logger log = LogManager.getLogger(XrayTrafficClient.class);
    private static final String TIMESTAMP_HEADER = "X-Ticastr-Traffic-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Ticastr-Traffic-Signature";

    private final RestTemplate restTemplate;
    private final XrayTrafficProperties properties;

    public XrayTrafficClient(RestTemplate restTemplate, XrayTrafficProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public TrafficMonitorVO.Overview overview() {
        return get("overview", TrafficMonitorVO.Overview.class, Map.of());
    }

    public List<TrafficMonitorVO.TrendPoint> timeseries(int hours) {
        return Arrays.asList(get("timeseries", TrafficMonitorVO.TrendPoint[].class, Map.of("hours", hours)));
    }

    public List<TrafficMonitorVO.DailyPoint> daily(int days) {
        return Arrays.asList(get("daily", TrafficMonitorVO.DailyPoint[].class, Map.of("days", days)));
    }

    public TrafficMonitorVO.SourcePage sources(int days, int limit, String search, boolean foreign) {
        return get("sources", TrafficMonitorVO.SourcePage.class, Map.of(
                "days", days,
                "limit", limit,
                "q", search == null ? "" : search,
                "foreign", foreign ? 1 : 0));
    }

    public List<TrafficMonitorVO.Target> targets(int days, int limit) {
        return Arrays.asList(get("targets", TrafficMonitorVO.Target[].class, Map.of("days", days, "limit", limit)));
    }

    public TrafficMonitorVO.Geo geo(int days) {
        return get("geo", TrafficMonitorVO.Geo.class, Map.of("days", days));
    }

    public List<TrafficMonitorVO.LiveConnection> live(int limit) {
        return Arrays.asList(get("live", TrafficMonitorVO.LiveConnection[].class, Map.of("limit", limit)));
    }

    public List<TrafficMonitorVO.Alert> alerts(int limit) {
        return Arrays.asList(get("alerts", TrafficMonitorVO.Alert[].class, Map.of("limit", limit)));
    }

    private <T> T get(String endpoint, Class<T> responseType, Map<String, ?> query) {
        if (!properties.isEnabled()) {
            throw new XrayTrafficUnavailableException("代理监控数据源未启用");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getSharedSecret() == null || properties.getSharedSecret().isBlank()) {
            throw new XrayTrafficUnavailableException("代理监控数据源配置不完整");
        }

        URI uri = buildUri(endpoint, query);
        long timestamp = Instant.now().getEpochSecond();
        String requestTarget = uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String canonical = "GET\n" + requestTarget + "\n" + timestamp;
        HttpHeaders headers = new HttpHeaders();
        headers.set(TIMESTAMP_HEADER, Long.toString(timestamp));
        headers.set(SIGNATURE_HEADER, sign(canonical, properties.getSharedSecret()));

        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), responseType);
            if (response.getBody() == null) {
                throw new XrayTrafficUnavailableException("代理监控数据源返回空响应");
            }
            return response.getBody();
        } catch (XrayTrafficUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Xray traffic bridge request failed: endpoint={}", endpoint);
            throw new XrayTrafficUnavailableException("代理监控数据源暂时不可用", exception);
        }
    }

    private URI buildUri(String endpoint, Map<String, ?> query) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getBaseUrl().replaceAll("/+$", ""))
                .path("/v1/")
                .path(endpoint);
        if (query != null) {
            query.forEach((key, value) -> builder.queryParam(key, value));
        }
        return builder.build().encode().toUri();
    }

    private String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成代理监控请求签名", exception);
        }
    }
}
