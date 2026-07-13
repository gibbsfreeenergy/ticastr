package com.wzh.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.OperationLogDao;
import com.wzh.blog.entity.OperationLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private static final String RETENTION_LOCK = "ticastr:lock:audit-log:retention";

    private final OperationLogDao operationLogDao;
    private final DistributedLockService distributedLockService;
    private final int retentionDays;

    public AuditLogService(OperationLogDao operationLogDao,
                           DistributedLockService distributedLockService,
                           @Value("${app.audit.retention-days:90}") int retentionDays) {
        this.operationLogDao = operationLogDao;
        this.distributedLockService = distributedLockService;
        this.retentionDays = retentionDays;
    }

    @Async("blogTaskExecutor")
    public void persist(OperationLog operationLog) {
        operationLogDao.insert(operationLog);
    }

    @Scheduled(cron = "${app.audit.cleanup-cron:0 30 3 * * ?}", zone = "Asia/Shanghai")
    public void removeExpiredLogs() {
        String token = distributedLockService.tryLock(RETENTION_LOCK, Duration.ofMinutes(30));
        if (token == null) {
            return;
        }
        try {
            operationLogDao.delete(new LambdaQueryWrapper<OperationLog>()
                    .lt(OperationLog::getCreateTime, LocalDateTime.now().minusDays(retentionDays)));
        } finally {
            distributedLockService.release(RETENTION_LOCK, token);
        }
    }
}
