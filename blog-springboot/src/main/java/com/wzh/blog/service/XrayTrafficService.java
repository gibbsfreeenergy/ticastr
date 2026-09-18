package com.wzh.blog.service;

import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import com.wzh.blog.vo.traffic.TrafficControlRequest;

import java.util.List;

public interface XrayTrafficService {

    TrafficMonitorVO.Overview overview();

    List<TrafficMonitorVO.TrendPoint> timeseries(int hours);

    List<TrafficMonitorVO.DailyPoint> daily(int days);

    TrafficMonitorVO.SourcePage sources(int days, int limit, String search, boolean foreign);

    List<TrafficMonitorVO.Target> targets(int days, int limit);

    TrafficMonitorVO.Geo geo(int days);

    List<TrafficMonitorVO.LiveConnection> live(int limit);

    List<TrafficMonitorVO.Alert> alerts(int limit);

    List<TrafficMonitorVO.BlocklistEntry> blocklist();

    List<TrafficMonitorVO.AlertRule> alertRules();

    TrafficMonitorVO.ControlResult block(TrafficControlRequest.BlockRequest request);

    TrafficMonitorVO.ControlResult unblock(TrafficControlRequest.IpRequest request);

    TrafficMonitorVO.ControlResult label(TrafficControlRequest.LabelRequest request);

    TrafficMonitorVO.ControlResult acknowledge(TrafficControlRequest.AlertAckRequest request);

    TrafficMonitorVO.ControlResult saveAlertRule(TrafficControlRequest.AlertRuleRequest request);

    TrafficMonitorVO.ControlResult deleteAlertRule(String id);

    TrafficMonitorVO.ControlResult syncBlocklist();

    TrafficMonitorVO.ControlResult collect();

    TrafficMonitorVO.ControlResult refreshGeo();
}
