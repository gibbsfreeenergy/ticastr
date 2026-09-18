package com.wzh.blog.controller;

import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.service.XrayTrafficService;
import com.wzh.blog.vo.Result;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
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

    private int range(Integer value, int fallback, int minimum, int maximum) {
        int normalized = value == null ? fallback : value;
        if (normalized < minimum || normalized > maximum) {
            throw new IllegalArgumentException("参数超出允许范围");
        }
        return normalized;
    }
}
