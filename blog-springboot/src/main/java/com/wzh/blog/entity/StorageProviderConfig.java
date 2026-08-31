package com.wzh.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Persisted active-provider selection; credentials remain deployment-only. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_storage_provider_config")
public class StorageProviderConfig {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    private String activeProvider;
    private LocalDateTime updatedAt;
    private Integer updatedBy;
}
