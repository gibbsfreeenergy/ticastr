package com.wzh.blog.service.impl;

import com.wzh.blog.infrastructure.traffic.XrayTrafficClient;
import com.wzh.blog.service.XrayTrafficService;
import com.wzh.blog.vo.traffic.TrafficMonitorVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class XrayTrafficServiceImpl implements XrayTrafficService {

    private final XrayTrafficClient client;

    public XrayTrafficServiceImpl(XrayTrafficClient client) {
        this.client = client;
    }

    @Override
    public TrafficMonitorVO.Overview overview() {
        return client.overview();
    }

    @Override
    public List<TrafficMonitorVO.TrendPoint> timeseries(int hours) {
        return client.timeseries(hours);
    }

    @Override
    public List<TrafficMonitorVO.DailyPoint> daily(int days) {
        return client.daily(days);
    }

    @Override
    public TrafficMonitorVO.SourcePage sources(int days, int limit, String search, boolean foreign) {
        return client.sources(days, limit, search, foreign);
    }

    @Override
    public List<TrafficMonitorVO.Target> targets(int days, int limit) {
        return client.targets(days, limit);
    }

    @Override
    public TrafficMonitorVO.Geo geo(int days) {
        return client.geo(days);
    }

    @Override
    public List<TrafficMonitorVO.LiveConnection> live(int limit) {
        return client.live(limit);
    }

    @Override
    public List<TrafficMonitorVO.Alert> alerts(int limit) {
        return client.alerts(limit);
    }
}
