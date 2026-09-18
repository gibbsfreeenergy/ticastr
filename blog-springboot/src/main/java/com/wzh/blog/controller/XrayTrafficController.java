package com.wzh.blog.controller;

import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.service.XrayTrafficService;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import com.wzh.blog.vo.traffic.TrafficControlRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "代理监控")
@RestController
@RequestMapping("/admin/traffic")
public class XrayTrafficController {

    private final XrayTrafficService trafficService;

    public XrayTrafficController(XrayTrafficService trafficService) {
        this.trafficService = trafficService;
    }

    @Operation(summary = "查看代理监控概览")
    @GetMapping("/overview")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<TrafficMonitorVO.Overview> overview() {
        return Result.ok(trafficService.overview());
    }

    @Operation(summary = "查看代理连接趋势")
    @GetMapping("/timeseries")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.TrendPoint>> timeseries(
            @RequestParam(defaultValue = "48") Integer hours) {
        return Result.ok(trafficService.timeseries(range(hours, 48, 1, 720)));
    }

    @Operation(summary = "查看代理每日趋势")
    @GetMapping("/daily")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.DailyPoint>> daily(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.ok(trafficService.daily(range(days, 30, 1, 90)));
    }

    @Operation(summary = "查看代理来源 IP")
    @GetMapping("/sources")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<TrafficMonitorVO.SourcePage> sources(
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "12") Integer limit,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean foreign) {
        String search = q == null ? "" : q.trim();
        if (search.length() > 80) {
            throw new IllegalArgumentException("搜索条件过长");
        }
        return Result.ok(trafficService.sources(
                range(days, 30, 1, 90), range(limit, 12, 1, 100), search, foreign));
    }

    @Operation(summary = "查看代理目标域名")
    @GetMapping("/targets")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.Target>> targets(
            @RequestParam(defaultValue = "30") Integer days,
            @RequestParam(defaultValue = "12") Integer limit) {
        return Result.ok(trafficService.targets(
                range(days, 30, 1, 90), range(limit, 12, 1, 100)));
    }

    @Operation(summary = "查看代理来源地理分布")
    @GetMapping("/geo")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<TrafficMonitorVO.Geo> geo(
            @RequestParam(defaultValue = "30") Integer days) {
        return Result.ok(trafficService.geo(range(days, 30, 1, 90)));
    }

    @Operation(summary = "查看代理实时连接")
    @GetMapping("/live")
    @AccessLimit(seconds = 10, maxCount = 30)
    public Result<List<TrafficMonitorVO.LiveConnection>> live(
            @RequestParam(defaultValue = "150") Integer limit) {
        return Result.ok(trafficService.live(range(limit, 150, 1, 300)));
    }

    @Operation(summary = "查看代理告警")
    @GetMapping("/alerts")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.Alert>> alerts(
            @RequestParam(defaultValue = "100") Integer limit) {
        return Result.ok(trafficService.alerts(range(limit, 100, 1, 200)));
    }

    @Operation(summary = "查看代理 IP 黑名单")
    @GetMapping("/blocklist")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.BlocklistEntry>> blocklist() {
        return Result.ok(trafficService.blocklist());
    }

    @Operation(summary = "封禁代理来源 IP")
    @PostMapping("/block")
    @AccessLimit(seconds = 10, maxCount = 10)
    public Result<TrafficMonitorVO.ControlResult> block(
            @Valid @RequestBody TrafficControlRequest.BlockRequest request) {
        return Result.ok(trafficService.block(request));
    }

    @Operation(summary = "解除代理来源 IP 封禁")
    @PostMapping("/unblock")
    @AccessLimit(seconds = 10, maxCount = 10)
    public Result<TrafficMonitorVO.ControlResult> unblock(
            @Valid @RequestBody TrafficControlRequest.IpRequest request) {
        return Result.ok(trafficService.unblock(request));
    }

    @Operation(summary = "更新代理来源 IP 备注")
    @PostMapping("/label")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<TrafficMonitorVO.ControlResult> label(
            @Valid @RequestBody TrafficControlRequest.LabelRequest request) {
        return Result.ok(trafficService.label(request));
    }

    @Operation(summary = "确认代理告警")
    @PostMapping("/alerts/ack")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<TrafficMonitorVO.ControlResult> acknowledge(
            @RequestBody TrafficControlRequest.AlertAckRequest request) {
        return Result.ok(trafficService.acknowledge(request));
    }

    @Operation(summary = "查看代理告警规则")
    @GetMapping("/alert-rules")
    @AccessLimit(seconds = 10, maxCount = 20)
    public Result<List<TrafficMonitorVO.AlertRule>> alertRules() {
        return Result.ok(trafficService.alertRules());
    }

    @Operation(summary = "保存代理告警规则")
    @PostMapping("/alert-rules")
    @AccessLimit(seconds = 10, maxCount = 10)
    public Result<TrafficMonitorVO.ControlResult> saveAlertRule(
            @Valid @RequestBody TrafficControlRequest.AlertRuleRequest request) {
        return Result.ok(trafficService.saveAlertRule(request));
    }

    @Operation(summary = "删除代理告警规则")
    @DeleteMapping("/alert-rules/{id}")
    @AccessLimit(seconds = 10, maxCount = 10)
    public Result<TrafficMonitorVO.ControlResult> deleteAlertRule(
            @PathVariable @Size(max = 64, message = "规则 ID 过长") String id) {
        return Result.ok(trafficService.deleteAlertRule(id));
    }

    @Operation(summary = "同步代理 IP 黑名单")
    @PostMapping("/blocklist/sync")
    @AccessLimit(seconds = 30, maxCount = 5)
    public Result<TrafficMonitorVO.ControlResult> syncBlocklist() {
        return Result.ok(trafficService.syncBlocklist());
    }

    @Operation(summary = "手动触发代理采集")
    @PostMapping("/collect")
    @AccessLimit(seconds = 30, maxCount = 3)
    public Result<TrafficMonitorVO.ControlResult> collect() {
        return Result.ok(trafficService.collect());
    }

    @Operation(summary = "刷新代理 GeoIP 信息")
    @PostMapping("/geo/refresh")
    @AccessLimit(seconds = 60, maxCount = 2)
    public Result<TrafficMonitorVO.ControlResult> refreshGeo() {
        return Result.ok(trafficService.refreshGeo());
    }

    private int range(Integer value, int fallback, int minimum, int maximum) {
        int normalized = value == null ? fallback : value;
        if (normalized < minimum || normalized > maximum) {
            throw new IllegalArgumentException("参数超出允许范围");
        }
        return normalized;
    }
}
