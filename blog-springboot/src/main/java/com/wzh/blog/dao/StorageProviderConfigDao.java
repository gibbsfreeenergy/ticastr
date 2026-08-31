package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.StorageProviderConfig;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface StorageProviderConfigDao extends BaseMapper<StorageProviderConfig> {

    StorageProviderConfig selectSingleton();

    int updateActive(@Param("provider") String provider,
                     @Param("updatedBy") Integer updatedBy,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
