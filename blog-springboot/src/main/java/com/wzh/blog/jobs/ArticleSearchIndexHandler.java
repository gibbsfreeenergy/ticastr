package com.wzh.blog.jobs;

import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.search.ArticleSearchApplicationService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Durable projection handler for article metadata/content changes. */
@Component
public class ArticleSearchIndexHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "ARTICLE_CONTENT_INDEX";

    private final ArticleSearchApplicationService searchService;

    public ArticleSearchIndexHandler(ArticleSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DurableEventEnvelope<?> event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("Invalid article index payload");
        }
        Object articleId = payload.get("articleId");
        if (!(articleId instanceof Number number)) {
            throw new IllegalArgumentException("Article index payload has no articleId");
        }
        searchService.indexArticle(number.intValue());
    }
}
