package com.wzh.blog.content;

import com.wzh.blog.dto.ArticleMetadataDTO;
import com.wzh.blog.vo.ArticleContentRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ArticleContentContractTest {

    @Test
    void rejectsMarkdownAboveOneMiBByUtf8Bytes() {
        MarkdownSanitizer sanitizer = new MarkdownSanitizer(1_048_576);
        String content = "中".repeat(1_048_577);

        assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(content));
    }

    @Test
    void stripsRawHtmlAndDangerousMarkdownUrls() {
        MarkdownSanitizer sanitizer = new MarkdownSanitizer(1_048_576);

        String sanitized = sanitizer.sanitize(
                "# title\n<script>alert(1)</script>\n"
                        + "[bad](javascript:alert(1))\n"
                        + "<img src=\"javascript:alert(1)\">");

        assertFalse(sanitized.contains("<script"));
        assertFalse(sanitized.contains("javascript:"));
        assertTrue(sanitized.contains("# title"));
    }

    @Test
    void acceptsValidUtf8AndArticleMetadataHasNoRawBody() {
        MarkdownSanitizer sanitizer = new MarkdownSanitizer(1_048_576);
        String content = "# 标题\n\n正文";

        assertEquals(content, sanitizer.sanitize(content));
        assertTrue(content.getBytes(StandardCharsets.UTF_8).length < 1_048_576);
        assertTrue(Arrays.stream(ArticleMetadataDTO.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("articleContent")));

        ArticleContentRequest request = new ArticleContentRequest(content, 2);
        assertEquals(content, request.content());
        assertEquals(2, request.expectedVersion());
    }
}
