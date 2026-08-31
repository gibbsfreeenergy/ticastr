package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "对象存储 provider 切换请求")
public record StorageProviderSwitchRequest(
        @NotBlank(message = "存储 provider 不能为空")
        @Schema(description = "local、oss、cos 或 tos", requiredMode = Schema.RequiredMode.REQUIRED)
        String provider) {
}
