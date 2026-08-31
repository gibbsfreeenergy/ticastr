package com.wzh.blog.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable metadata for an immutable Markdown object. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_content_asset")
public class ContentAsset {

    @TableId(value = "asset_id", type = IdType.INPUT)
    private String assetId;

    private Integer articleId;
    private String provider;
    private String objectKey;
    private String contentType;
    private String format;
    private Integer version;
    private String checksum;
    private Long sizeBytes;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String lastError;

    public boolean isActive() {
        return ContentAssetStatus.ACTIVE.name().equals(status);
    }

    public ContentAssetStatus lifecycleStatus() {
        return ContentAssetStatus.valueOf(status);
    }
}
