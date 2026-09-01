package com.wzh.blog.vo;

import java.util.List;

public record StorageConfigListResponse(
        Long activeConfigId,
        List<StorageConfigSummaryVO> configs) {
}
