package com.wzh.blog.service;

import com.wzh.blog.vo.traffic.TrafficMonitorVO;

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
}
