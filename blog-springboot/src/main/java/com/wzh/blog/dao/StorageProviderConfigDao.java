package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.StorageProviderConfig;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StorageProviderConfigDao extends BaseMapper<StorageProviderConfig> {

    List<StorageProviderConfig> selectAll();

    StorageProviderConfig selectById(@Param("id") Long id);

    StorageProviderConfig selectByIdForUpdate(@Param("id") Long id);

    StorageProviderConfig selectActive();

    int insertProfile(StorageProviderConfig config);

    int updateProfile(StorageProviderConfig config);

    int upsertLegacyProfile(StorageProviderConfig config);

    int updateValidation(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("validatedAt") LocalDateTime validatedAt,
                         @Param("message") String message);

    int updateUsage(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("objectCount") Long objectCount,
                    @Param("bytes") Long bytes,
                    @Param("lastModified") LocalDateTime lastModified,
                    @Param("checkedAt") LocalDateTime checkedAt,
                    @Param("error") String error);

    int activateOnly(@Param("id") Long id,
                     @Param("updatedBy") Integer updatedBy,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int countByProvider(@Param("provider") String provider);

    int countAssetReferences(@Param("id") Long id);

    StorageProviderConfig selectSingleton();

    int updateActive(@Param("provider") String provider,
                     @Param("updatedBy") Integer updatedBy,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
