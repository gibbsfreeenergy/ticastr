package com.wzh.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Content metadata returned before the Markdown stream is fetched. */
@Schema(description = "文章内容资源信息")
public record ArticleContentResponse(
        Integer articleId,
        Integer version,
        String contentType,
        long sizeBytes,
        String checksum,
        Instant lastModified,
        String contentUrl) {
}
