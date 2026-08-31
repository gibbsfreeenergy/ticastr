package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.content.ContentAsset;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentAssetDao extends BaseMapper<ContentAsset> {

    ContentAsset selectActiveByArticleId(@Param("articleId") Integer articleId);

    ContentAsset selectByIdForUpdate(@Param("assetId") String assetId);

    ContentAsset selectByArticleIdAndVersion(@Param("articleId") Integer articleId,
                                             @Param("version") Integer version);

    Integer selectMaxVersion(@Param("articleId") Integer articleId);

    int activate(@Param("assetId") String assetId,
                 @Param("checksum") String checksum,
                 @Param("sizeBytes") long sizeBytes,
                 @Param("updatedAt") java.time.LocalDateTime updatedAt);

    int retire(@Param("assetId") String assetId,
               @Param("updatedAt") java.time.LocalDateTime updatedAt);

    int updateStatus(@Param("assetId") String assetId,
                     @Param("status") String status,
                     @Param("lastError") String lastError,
                     @Param("deletedAt") java.time.LocalDateTime deletedAt,
                     @Param("updatedAt") java.time.LocalDateTime updatedAt);

    List<ContentAsset> listRetiredForCleanup(@Param("limit") int limit);

    List<ContentAsset> listVersions(@Param("articleId") Integer articleId,
                                    @Param("afterVersion") Integer afterVersion,
                                    @Param("limit") int limit);

    List<ContentAsset> listCleanupCandidates(@Param("limit") int limit);
}
