package com.wzh.blog.vo.traffic;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/** Public response shapes for the admin traffic monitor. */
public final class TrafficMonitorVO {

    private TrafficMonitorVO() {
    }

    public record Collector(
            Long at,
            Long lag) {
    }

    public record Overview(
            @JsonAlias("total_conns") long totalConnections,
            @JsonAlias("total_ips") long totalIps,
            @JsonAlias("today_conns") long todayConnections,
            @JsonAlias("total_domains") long totalDomains,
            @JsonAlias("first_conn") String firstConnection,
            @JsonAlias("last_conn") String lastConnection,
            @JsonAlias("alerts_open") long openAlerts,
            @JsonAlias("traffic_up") long trafficUp,
            @JsonAlias("traffic_down") long trafficDown,
            long online,
            String xray,
            Collector collector,
            String timezone,
            @JsonAlias("generated_at") long generatedAt) {
    }

    public record TrendPoint(
            long bucket,
            String label,
            @JsonAlias("conns") long connections,
            @JsonAlias("ips") long uniqueIps,
            long up,
            long down) {
    }

    public record DailyPoint(
            String label,
            @JsonAlias("conns") long connections,
            @JsonAlias("ips") long uniqueIps) {
    }

    public record Source(
            @JsonAlias("src") String ip,
            @JsonAlias("n") long connections,
            @JsonAlias("hosts") long targets,
            String cc,
            String prov,
            String place,
            String org,
            String label,
            int blocked,
            String first,
            String last) {
    }

    public record SourcePage(
            long total,
            List<Source> items) {
    }

    public record Target(
            @JsonAlias("host") String domain,
            @JsonAlias("n") long connections,
            @JsonAlias("ips") long sources,
            String first,
            String last) {
    }

    public record LiveConnection(
            String t,
            long ts,
            @JsonAlias("src") String sourceIp,
            @JsonAlias("net") String network,
            @JsonAlias("host") String target,
            Integer port) {
    }

    public record Country(
            @JsonAlias("cc") String code,
            @JsonAlias("n") long connections,
            @JsonAlias("ips") long sources) {
    }

    public record Province(
            @JsonAlias("prov") String name,
            @JsonAlias("n") long connections,
            @JsonAlias("ips") long sources) {
    }

    public record Organization(
            @JsonAlias("org") String name,
            @JsonAlias("n") long connections) {
    }

    public record Geo(
            List<Country> countries,
            List<Province> provinces,
            List<Organization> orgs) {
    }

    public record Alert(
            long id,
            String t,
            String level,
            String kind,
            String title,
            String detail,
            int acked) {
    }
}
