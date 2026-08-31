package com.wzh.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Persisted managed storage profile. Credential fields remain encrypted at rest. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_storage_provider_config")
public class StorageProviderConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String configName;
    private String provider;
    private String endpoint;
    private String bucket;
    private String region;
    private String localRoot;
    private String publicUrl;
    private String accessKeyIdCiphertext;
    private String accessKeySecretCiphertext;
    @TableField("is_active")
    private Boolean active;
    private String configSource;
    private String lastValidationStatus;
    private LocalDateTime lastValidationAt;
    private String lastValidationMessage;
    private String usageStatus;
    private Long usageObjectCount;
    private Long usageBytes;
    private LocalDateTime usageLastModified;
    private LocalDateTime usageCheckedAt;
    private String usageError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer updatedBy;

    public String getActiveProvider() {
        return provider;
    }
}
