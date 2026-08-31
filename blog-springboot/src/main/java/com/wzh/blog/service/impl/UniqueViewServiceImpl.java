package com.wzh.blog.service.impl;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.wzh.blog.dto.UniqueViewDTO;
import com.wzh.blog.entity.UniqueView;
import com.wzh.blog.dao.UniqueViewDao;
import com.wzh.blog.service.UniqueViewService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;



/**
 * 访问量统计服务
 *
 * @author yezhiqiu
 * @date 2021/08/06
 */
@Service
public class UniqueViewServiceImpl extends ServiceImpl<UniqueViewDao, UniqueView> implements UniqueViewService {
    private final UniqueViewDao uniqueViewDao;

    public UniqueViewServiceImpl(UniqueViewDao uniqueViewDao) {
        this.uniqueViewDao = uniqueViewDao;
    }




    @Override
    public List<UniqueViewDTO> listUniqueViews() {
        DateTime startTime = DateUtil.beginOfDay(DateUtil.offsetDay(new Date(), -7));
        DateTime endTime = DateUtil.endOfDay(new Date());
        return uniqueViewDao.listUniqueViews(startTime, endTime);
    }

    @Override
    @Transactional
    public void recordVisitor(String visitorHash, String area) {
        if (visitorHash == null || visitorHash.isBlank()) {
            return;
        }
        if (uniqueViewDao.insertVisitor(visitorHash, area) == 1) {
            uniqueViewDao.incrementDailyUniqueView();
        }
    }

    @Override
    public long totalViews() {
        return uniqueViewDao.totalViews();
    }

}
