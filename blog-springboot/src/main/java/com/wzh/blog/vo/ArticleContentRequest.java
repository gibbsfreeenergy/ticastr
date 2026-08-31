package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A version-checked content write. The content body is never mapped to tb_article. */
@Schema(description = "文章 Markdown 内容写入请求")
public record ArticleContentRequest(
        @NotBlank(message = "文章内容不能为空")
        @Size(max = 1_048_576, message = "文章内容不能超过1MiB")
        @Schema(description = "Markdown 内容", requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
        @Schema(description = "期望的当前版本；新文章可为空")
        Integer expectedVersion) {
}
