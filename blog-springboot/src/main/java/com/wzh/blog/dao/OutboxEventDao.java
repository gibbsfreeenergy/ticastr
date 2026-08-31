package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.OutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventDao extends BaseMapper<OutboxEvent> {

    List<OutboxEvent> listClaimable(@Param("limit") int limit);

    List<OutboxEvent> listEnqueueable(@Param("limit") int limit);

    long countByStatus(@Param("status") String status);

    long countAll();

    List<OutboxEvent> listRecent(@Param("offset") long offset, @Param("size") int size);

    boolean existsOpen(@Param("eventType") String eventType, @Param("aggregateId") String aggregateId);
}
