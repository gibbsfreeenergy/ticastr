package com.wzh.blog.search;

import com.wzh.blog.content.ArticleContentService;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.service.OutboxEventService;
import com.wzh.blog.web.CursorCodec;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;

/** 构建并查询本地文章搜索投影。 */
@Service
@Log4j2
public class ArticleSearchApplicationService {

    private static final int MAX_CONTENT_BYTES = 1_048_576;

    private final ArticleSearchIndex index;
    private final ArticleDao articleDao;
    private final ArticleContentService contentService;
    private final OutboxEventService outboxEventService;
    private final CursorCodec cursorCodec;

    public ArticleSearchApplicationService(ArticleSearchIndex index,
                                           ArticleDao articleDao,
                                           ArticleContentService contentService,
                                           OutboxEventService outboxEventService,
                                           CursorCodec cursorCodec) {
        this.index = index;
        this.articleDao = articleDao;
        this.contentService = contentService;
        this.outboxEventService = outboxEventService;
        this.cursorCodec = cursorCodec;
    }

    public CursorPageResult<ArticleSearchDTO> search(String query, CursorPageQuery pageQuery) {
        String normalizedQuery = query == null ? "" : query.trim();
        String fingerprint = cursorCodec.fingerprint("search:" + normalizedQuery);
        int offset = pageQuery.cursor() == null
                ? 0 : cursorCodec.decodeOffset(pageQuery.cursor(), fingerprint);
        List<VisibleResult> rows = visibleResults(normalizedQuery, offset, pageQuery.size() + 1);
        boolean hasNext = rows.size() > pageQuery.size();
        List<ArticleSearchDTO> items = rows.stream().limit(pageQuery.size())
                .map(VisibleResult::article).toList();
        String nextCursor = hasNext
                ? cursorCodec.encodeOffset(rows.get(pageQuery.size()).offset(), fingerprint)
                : null;
        return new CursorPageResult<>(items, nextCursor, hasNext);
    }

    private List<VisibleResult> visibleResults(String query, int offset, int limit) {
        List<VisibleResult> visible = new ArrayList<>();
        int position = offset;
        while (visible.size() < limit && position < Integer.MAX_VALUE) {
            List<ArticleSearchResult> candidates = index.search(query, position, limit - visible.size());
            if (candidates.isEmpty()) {
                break;
            }
            Map<Integer, Article> current = new HashMap<>();
            articleDao.selectByIds(candidates.stream().map(ArticleSearchResult::articleId).toList())
                    .forEach(article -> current.put(article.getId(), article));
            for (ArticleSearchResult candidate : candidates) {
                Article article = current.get(candidate.articleId());
                if (article != null
                        && Integer.valueOf(FALSE).equals(article.getIsDelete())
                        && PUBLIC.getStatus().equals(article.getStatus())) {
                    visible.add(new VisibleResult(ArticleSearchDTO.builder()
                            .id(article.getId())
                            .articleTitle(article.getArticleTitle())
                            .snippet(candidate.snippet())
                            .isDelete(FALSE)
                            .status(PUBLIC.getStatus())
                            .build(), position));
                }
                position++;
                if (visible.size() == limit || position == Integer.MAX_VALUE) {
                    break;
                }
            }
        }
        return visible;
    }

    private record VisibleResult(ArticleSearchDTO article, int offset) { }

    /** 将文章元数据变更排入同一事务的索引事件。 */
    public void scheduleIndex(Integer articleId) {
        if (articleId != null) {
            outboxEventService.enqueue("ARTICLE_CONTENT_INDEX", 1, String.valueOf(articleId), null,
                    Map.of("articleId", articleId));
        }
    }

    public synchronized void indexArticle(Integer articleId) {
        if (articleId == null) {
            return;
        }
        Article article = articleDao.selectById(articleId);
        if (article == null
                || !Integer.valueOf(FALSE).equals(article.getIsDelete())
                || !PUBLIC.getStatus().equals(article.getStatus())) {
            index.delete(articleId);
            return;
        }
        ContentAsset asset = contentService.currentPublicAssetOrNull(articleId);
        if (asset == null) {
            index.delete(articleId);
            return;
        }
        index.upsert(new ArticleSearchDocument(article.getId(), article.getArticleTitle(),
                readContent(article.getId())));
    }

    public synchronized void rebuildAll() {
        List<ArticleSearchDocument> documents = new ArrayList<>();
        int afterId = 0;
        while (true) {
            List<Article> page = articleDao.listPublishedArticlesAfter(afterId, 100);
            if (page.isEmpty()) {
                break;
            }
            for (Article article : page) {
                try {
                    ContentAsset asset = contentService.currentPublicAssetOrNull(article.getId());
                    if (asset != null) {
                        documents.add(new ArticleSearchDocument(article.getId(), article.getArticleTitle(),
                                readContent(article.getId())));
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Unable to rebuild search for article " + article.getId(), exception);
                }
                afterId = article.getId();
            }
        }
        index.rebuild(documents);
    }

    public long documentCount() {
        return index.documentCount();
    }

    private String readContent(Integer articleId) {
        try (StorageObject object = contentService.openPublic(articleId)) {
            byte[] bytes = object.content().readNBytes(MAX_CONTENT_BYTES + 1);
            if (bytes.length > MAX_CONTENT_BYTES) {
                throw new IllegalStateException("Article content exceeds search index limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read article content for search", exception);
        }
    }
}
