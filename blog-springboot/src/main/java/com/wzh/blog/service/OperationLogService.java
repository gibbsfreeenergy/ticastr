package com.wzh.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wzh.blog.dto.OperationLogDTO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.entity.OperationLog;
import com.wzh.blog.vo.ConditionVO;

/**
 * 操作日志服务
 *
 * @author yezhiqiu
 * @date 2021/07/29
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 查询日志列表
     *
     * @param conditionVO 条件
     * @return 日志列表
     */
    PageResult<OperationLogDTO> listOperationLogs(ConditionVO conditionVO);

}
