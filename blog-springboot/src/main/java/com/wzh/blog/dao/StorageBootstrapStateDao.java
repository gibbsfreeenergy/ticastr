package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.StorageBootstrapState;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface StorageBootstrapStateDao extends BaseMapper<StorageBootstrapState> {

    StorageBootstrapState selectForUpdate();

    int markCompleted(@Param("completedAt") LocalDateTime completedAt);
}
