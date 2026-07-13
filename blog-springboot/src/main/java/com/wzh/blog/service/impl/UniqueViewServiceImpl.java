package com.wzh.blog.service.impl;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.wzh.blog.dto.UniqueViewDTO;
import com.wzh.blog.entity.UniqueView;
import com.wzh.blog.dao.UniqueViewDao;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.service.DistributedLockService;
import com.wzh.blog.service.UniqueViewService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static com.wzh.blog.constant.RedisPrefixConst.UNIQUE_VISITOR;
import static com.wzh.blog.constant.RedisPrefixConst.VISITOR_AREA;
import static com.wzh.blog.enums.ZoneEnum.SHANGHAI;


/**
 * 访问量统计服务
 *
 * @author yezhiqiu
 * @date 2021/08/06
 */
@Service
public class UniqueViewServiceImpl extends ServiceImpl<UniqueViewDao, UniqueView> implements UniqueViewService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private UniqueViewDao uniqueViewDao;
    @Autowired
    private DistributedLockService distributedLockService;

    private static final String SAVE_LOCK = "ticastr:lock:unique-view:save";
    private static final String CLEAR_LOCK = "ticastr:lock:unique-view:clear";




    @Override
    public List<UniqueViewDTO> listUniqueViews() {
        DateTime startTime = DateUtil.beginOfDay(DateUtil.offsetDay(new Date(), -7));
        DateTime endTime = DateUtil.endOfDay(new Date());
        return uniqueViewDao.listUniqueViews(startTime, endTime);
    }

    @Scheduled(cron = " 0 0 0 * * ?", zone = "Asia/Shanghai")
    public void saveUniqueView() {
        String token = distributedLockService.tryLock(SAVE_LOCK, Duration.ofMinutes(5));
        if (token == null) {
            return;
        }
        try {
            Long count = redisService.sSize(UNIQUE_VISITOR);
            UniqueView uniqueView = UniqueView.builder()
                    .createTime(LocalDateTimeUtil.offset(
                            LocalDateTime.now(ZoneId.of(SHANGHAI.getZone())), -1, ChronoUnit.DAYS))
                    .viewsCount(count == null ? 0 : count.intValue())
                    .build();
            uniqueViewDao.upsertDailyUniqueView(uniqueView);
        } finally {
            distributedLockService.release(SAVE_LOCK, token);
        }
    }

    @Scheduled(cron = " 0 1 0 * * ?", zone = "Asia/Shanghai")
    public void clear() {
        String token = distributedLockService.tryLock(CLEAR_LOCK, Duration.ofMinutes(5));
        if (token == null) {
            return;
        }
        try {
            redisService.del(UNIQUE_VISITOR);
            redisService.del(VISITOR_AREA);
        } finally {
            distributedLockService.release(CLEAR_LOCK, token);
        }
    }

}
