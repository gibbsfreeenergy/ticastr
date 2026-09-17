package com.wzh.blog.search;

import com.wzh.blog.content.ArticleContentService;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.bootstrap.ArticleSearchBootstrapRunner;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.OutboxEventDao;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.OutboxEvent;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.infrastructure.search.LuceneArticleSearchIndex;
import com.wzh.blog.service.OutboxEventService;
import com.wzh.blog.web.CursorCodec;
import com.wzh.blog.web.CursorPageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleSearchApplicationServiceTest {
    @TempDir Path temporaryDirectory;
    private final ArticleDao articles = mock(ArticleDao.class);
    private final OutboxEventService outbox = mock(OutboxEventService.class);
    private final Map<Integer, Article> currentArticles = new HashMap<>();
    private ArticleSearchApplicationService service;
    private LuceneArticleSearchIndex index;
    private final ArticleContentService content = mock(ArticleContentService.class);

    @BeforeEach
    void setUp() {
        index = new LuceneArticleSearchIndex(temporaryDirectory.resolve("index"));
        index.rebuild(IntStream.rangeClosed(1, 61).mapToObj(id -> {
            currentArticles.put(id, Article.builder().id(id).articleTitle("Current " + id)
                    .isDelete(0).status(1).build());
            return new ArticleSearchDocument(id, "Search " + id, "search body " + id);
        }).toList());
        when(articles.selectByIds(anyCollection())).thenAnswer(call ->
                ((Collection<?>) call.getArgument(0)).stream().map(currentArticles::get)
                        .filter(java.util.Objects::nonNull).toList());
        service = new ArticleSearchApplicationService(index, articles, content, outbox,
                new CursorCodec("test-search-key", 900L));
    }

    @Test
    void rechecksVisibilityAndFillsPagesAcrossStaleIndexEntries() {
        currentArticles.get(1).setStatus(2);
        currentArticles.get(2).setIsDelete(1);
        currentArticles.remove(3);
        var first = service.search("search", new CursorPageQuery(null, 20));
        assertThat(first.items()).hasSize(20).extracting(ArticleSearchDTO::getId)
                .doesNotContain(1, 2, 3).doesNotHaveDuplicates();
        assertThat(first.hasNext()).isTrue();
        var second = service.search("search", new CursorPageQuery(first.nextCursor(), 20));
        assertThat(second.items()).hasSize(20).extracting(ArticleSearchDTO::getId)
                .doesNotContainAnyElementsOf(first.items().stream().map(ArticleSearchDTO::getId).toList());
        var third = service.search("search", new CursorPageQuery(second.nextCursor(), 20));
        assertThat(third.items()).hasSize(18);
        assertThat(third.hasNext()).isFalse();
    }

    @Test
    void supportsMaximumPageSizeDespiteSmallerIndexBatchLimit() {
        var first = service.search("search", new CursorPageQuery(null, 50));
        assertThat(first.items()).hasSize(50);
        assertThat(first.hasNext()).isTrue();
        var second = service.search("search", new CursorPageQuery(first.nextCursor(), 50));
        assertThat(second.items()).hasSize(11);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void createsAnEventForEveryEditEvenWhenAnOlderEventIsProcessingOrDead() {
        var events = mock(OutboxEventDao.class);
        when(events.existsOpen("ARTICLE_CONTENT_INDEX", "1")).thenReturn(true);
        List<OutboxEvent> inserted = new ArrayList<>();
        when(events.insert(any(OutboxEvent.class))).thenAnswer(call -> {
            inserted.add(call.getArgument(0));
            return 1;
        });
        service = new ArticleSearchApplicationService(index, articles, content,
                new OutboxEventService(events), new CursorCodec("test-search-key", 900L));
        service.scheduleIndex(1);
        service.scheduleIndex(1);
        assertThat(inserted).hasSize(2).extracting(OutboxEvent::getEventId).doesNotHaveDuplicates();
        assertThat(inserted).allSatisfy(event -> {
            assertThat(event.getStatus()).isEqualTo("PENDING");
            assertThat(event.getAggregateId()).isEqualTo("1");
            assertThat(event.getEventType()).isEqualTo("ARTICLE_CONTENT_INDEX");
        });
    }

    @Test
    void restartRebuildsAnEmptyIndexFromPublishedContent() {
        index.rebuild(List.of());
        when(articles.listPublishedArticlesAfter(0, 100)).thenReturn(List.of(currentArticles.get(1)));
        when(content.currentPublicAssetOrNull(1)).thenReturn(ContentAsset.builder().articleId(1).build());
        byte[] body = "Recovered searchable content".getBytes(StandardCharsets.UTF_8);
        when(content.openPublic(1)).thenReturn(new StorageObject(new ByteArrayInputStream(body),
                new StorageObjectMetadata("articles/1/test.md", "text/markdown", body.length, "checksum", null)));
        new ArticleSearchBootstrapRunner(service).run(null);

        assertThat(service.search("Recovered", new CursorPageQuery(null, 10)).items())
                .extracting(ArticleSearchDTO::getId).containsExactly(1);
    }

    @Test
    void storageFailureDuringRestartPreservesTheExistingIndex() {
        when(articles.listPublishedArticlesAfter(0, 100)).thenReturn(List.of(currentArticles.get(1)));
        when(content.currentPublicAssetOrNull(1)).thenThrow(new IllegalStateException("storage offline"));

        new ArticleSearchBootstrapRunner(service).run(null);

        assertThat(index.documentCount()).isEqualTo(61);
        assertThat(service.search("search", new CursorPageQuery(null, 10)).items()).hasSize(10);
    }
}
