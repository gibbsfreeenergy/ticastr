package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "对象存储配置档案请求")
public record StorageConfigRequest(
        String name,
        String provider,
        String endpoint,
        String region,
        String bucket,
        String localRoot,
        String publicUrl,
        String accessKeyId,
        String accessKeySecret) {
}
