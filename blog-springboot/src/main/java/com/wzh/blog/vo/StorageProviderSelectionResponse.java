package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "对象存储 provider 选择")
public record StorageProviderSelectionResponse(
        String activeProvider,
        Long activeConfigId,
        List<String> supportedProviders) {

    /** @deprecated Compatibility constructor for provider-only clients. */
    @Deprecated
    public StorageProviderSelectionResponse(String activeProvider, List<String> supportedProviders) {
        this(activeProvider, null, supportedProviders);
    }
}
