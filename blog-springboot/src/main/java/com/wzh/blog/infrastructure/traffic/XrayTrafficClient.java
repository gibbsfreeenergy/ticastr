package com.wzh.blog.infrastructure.traffic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzh.blog.config.XrayTrafficProperties;
import com.wzh.blog.exception.XrayTrafficUnavailableException;
import com.wzh.blog.vo.traffic.TrafficControlRequest;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed HTTP client for the DMIT traffic bridge and its allowlisted controls. */
@Component
public class XrayTrafficClient {

    private static final Logger log = LogManager.getLogger(XrayTrafficClient.class);
    private static final String TIMESTAMP_HEADER = "X-Ticastr-Traffic-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Ticastr-Traffic-Signature";
    private static final String BODY_HASH_HEADER = "X-Ticastr-Traffic-Body-SHA256";

    private final RestTemplate restTemplate;
    private final XrayTrafficProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public List<TrafficMonitorVO.BlocklistEntry> blocklist() {
        return Arrays.asList(get("blocklist", TrafficMonitorVO.BlocklistEntry[].class, Map.of()));
    }

    public List<TrafficMonitorVO.AlertRule> alertRules() {
        return Arrays.asList(get("alert-rules", TrafficMonitorVO.AlertRule[].class, Map.of()));
    }

    public TrafficMonitorVO.ControlResult block(TrafficControlRequest.BlockRequest request) {
        return post("block", Map.of("ip", request.ip(), "reason", request.reason() == null ? "" : request.reason()));
    }

    public TrafficMonitorVO.ControlResult unblock(TrafficControlRequest.IpRequest request) {
        return post("unblock", Map.of("ip", request.ip()));
    }

    public TrafficMonitorVO.ControlResult label(TrafficControlRequest.LabelRequest request) {
        return post("label", Map.of("ip", request.ip(), "label", request.label() == null ? "" : request.label()));
    }

    public TrafficMonitorVO.ControlResult acknowledge(TrafficControlRequest.AlertAckRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request.id() != null) {
            payload.put("id", request.id());
        }
        payload.put("all", Boolean.TRUE.equals(request.all()));
        return post("alerts/ack", payload);
    }

    public TrafficMonitorVO.ControlResult saveAlertRule(TrafficControlRequest.AlertRuleRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", request.id());
        payload.put("metric", request.metric());
        payload.put("threshold", request.threshold());
        payload.put("window_sec", request.windowSec());
        payload.put("cooldown_sec", request.cooldownSec());
        payload.put("level", request.level() == null || request.level().isBlank() ? "info" : request.level());
        payload.put("enabled", !Boolean.FALSE.equals(request.enabled()));
        payload.put("email", request.email() == null ? "" : request.email());
        return post("alert-rules", payload);
    }

    public TrafficMonitorVO.ControlResult deleteAlertRule(String id) {
        return post("alert-rules/delete", Map.of("id", id));
    }

    public TrafficMonitorVO.ControlResult syncBlocklist() {
        return post("blocklist/sync", Map.of());
    }

    public TrafficMonitorVO.ControlResult collect() {
        return post("collect", Map.of());
    }

    public TrafficMonitorVO.ControlResult refreshGeo() {
        return post("geo/refresh", Map.of());
    }

    private <T> T get(String endpoint, Class<T> responseType, Map<String, ?> query) {
        ensureConfigured();

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

    private TrafficMonitorVO.ControlResult post(String endpoint, Object payload) {
        ensureConfigured();
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法编码代理监控操作请求", exception);
        }
        URI uri = buildUri(endpoint, Map.of());
        long timestamp = Instant.now().getEpochSecond();
        String bodyDigest = sha256(body);
        String requestTarget = uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        String canonical = "POST\n" + requestTarget + "\n" + timestamp + "\n" + bodyDigest;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TIMESTAMP_HEADER, Long.toString(timestamp));
        headers.set(BODY_HASH_HEADER, bodyDigest);
        headers.set(SIGNATURE_HEADER, sign(canonical, properties.getSharedSecret()));
        try {
            ResponseEntity<TrafficMonitorVO.ControlResult> response = restTemplate.exchange(
                    uri, HttpMethod.POST, new HttpEntity<>(body, headers), TrafficMonitorVO.ControlResult.class);
            if (response.getBody() == null) {
                throw new XrayTrafficUnavailableException("代理监控数据源返回空响应");
            }
            return response.getBody();
        } catch (XrayTrafficUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Xray traffic bridge control request failed: endpoint={}", endpoint);
            throw new XrayTrafficUnavailableException("代理监控操作暂时不可用", exception);
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new XrayTrafficUnavailableException("代理监控数据源未启用");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getSharedSecret() == null || properties.getSharedSecret().isBlank()) {
            throw new XrayTrafficUnavailableException("代理监控数据源配置不完整");
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

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成代理监控请求摘要", exception);
        }
    }
}
