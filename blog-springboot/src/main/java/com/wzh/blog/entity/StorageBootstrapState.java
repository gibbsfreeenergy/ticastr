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

/** One-row state for importing legacy environment storage settings. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_storage_bootstrap_state")
public class StorageBootstrapState {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    @TableField("legacy_import_completed")
    private Boolean legacyImportCompleted;

    private String legacyActiveProvider;
    private LocalDateTime completedAt;
}
