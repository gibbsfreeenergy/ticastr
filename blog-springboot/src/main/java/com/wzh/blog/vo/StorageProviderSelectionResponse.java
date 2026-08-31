package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "对象存储 provider 选择")
public record StorageProviderSelectionResponse(
        String activeProvider,
        List<String> supportedProviders) {
}
