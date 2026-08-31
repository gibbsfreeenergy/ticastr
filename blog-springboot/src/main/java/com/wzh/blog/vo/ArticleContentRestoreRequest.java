package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Optimistic-lock input for restoring a prior immutable content version. */
@Schema(description = "文章内容版本恢复请求")
public record ArticleContentRestoreRequest(
        @Schema(description = "当前编辑器期望的版本") Integer expectedVersion) {
}
