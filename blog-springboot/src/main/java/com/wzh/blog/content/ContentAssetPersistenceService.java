package com.wzh.blog.content;

import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.ContentAssetDao;
import com.wzh.blog.entity.Article;
import com.wzh.blog.exception.ConflictException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.service.OutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
import java.util.List;

/**
 * Owns the short MySQL transactions that reserve and publish content assets.
 * Object storage I/O is intentionally performed by the caller outside these
 * transactions.
 */
@Service
public class ContentAssetPersistenceService {

    private final ArticleDao articleDao;
    private final ContentAssetDao contentAssetDao;
    private final OutboxEventService outboxEventService;

    public ContentAssetPersistenceService(ArticleDao articleDao,
                                          ContentAssetDao contentAssetDao,
                                          OutboxEventService outboxEventService) {
        this.articleDao = articleDao;
        this.contentAssetDao = contentAssetDao;
        this.outboxEventService = outboxEventService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ContentAssetReservation reserve(Integer articleId,
                                           Integer expectedVersion,
                                           long sizeBytes,
                                           String provider,
                                           String contentType,
                                           String format) {
        Article article = articleDao.selectForUpdate(articleId);
        if (article == null || !Integer.valueOf(0).equals(article.getIsDelete())) {
            throw new NotFoundException("文章不存在");
        }
        ContentAsset previousActive = contentAssetDao.selectActiveByArticleId(articleId);
        int currentVersion = previousActive == null || previousActive.getVersion() == null
                ? 0 : previousActive.getVersion();
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw new ConflictException("文章内容版本已更新，请刷新后重试");
        }
        Integer maximumVersion = contentAssetDao.selectMaxVersion(articleId);
        int nextVersion = Math.max(currentVersion, maximumVersion == null ? 0 : maximumVersion) + 1;
        String assetId = UUID.randomUUID().toString();
        String objectKey = "articles/" + articleId + "/" + nextVersion + "-" + assetId + ".md";
        LocalDateTime now = LocalDateTime.now();
        ContentAsset asset = ContentAsset.builder()
                .assetId(assetId)
                .articleId(articleId)
                .provider(provider)
                .objectKey(objectKey)
                .contentType(contentType)
                .format(format)
                .version(nextVersion)
                .sizeBytes(sizeBytes)
                .status(ContentAssetStatus.PENDING.name())
                .createdAt(now)
                .updatedAt(now)
                .build();
        contentAssetDao.insert(asset);
        return new ContentAssetReservation(asset, previousActive);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void publish(String assetId, Integer articleId, String previousAssetId,
                        String checksum, long sizeBytes) {
        Article article = articleDao.selectForUpdate(articleId);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        ContentAsset asset = contentAssetDao.selectByIdForUpdate(assetId);
        if (asset == null || !ContentAssetStatus.PENDING.name().equals(asset.getStatus())) {
            throw new ConflictException("文章内容资产状态无效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (previousAssetId != null) {
            contentAssetDao.retire(previousAssetId, now);
            outboxEventService.enqueueIfAbsent("CONTENT_ASSET_DELETE", 1, previousAssetId, null,
                    Map.of("assetId", previousAssetId));
        }
        if (contentAssetDao.activate(assetId, checksum, sizeBytes, now) != 1) {
            throw new ConflictException("文章内容资产无法激活");
        }
        Article pointer = Article.builder().id(articleId).contentAssetId(assetId).build();
        if (articleDao.updateById(pointer) != 1) {
            throw new ConflictException("文章内容指针更新失败");
        }
        // The index event is part of the same transaction as the new active pointer.
        outboxEventService.enqueue("ARTICLE_CONTENT_INDEX", 1, String.valueOf(articleId), null,
                Map.of("articleId", articleId, "version", asset.getVersion()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String assetId, String error) {
        contentAssetDao.updateStatus(assetId,
                ContentAssetStatus.DELETE_FAILED.name(),
                truncate(error),
                null,
                LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeleted(String assetId) {
        contentAssetDao.updateStatus(assetId,
                ContentAssetStatus.DELETED.name(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeleteFailed(String assetId, String error) {
        contentAssetDao.updateStatus(assetId,
                ContentAssetStatus.DELETE_FAILED.name(),
                truncate(error),
                null,
                LocalDateTime.now());
    }

    public List<ContentAsset> listVersions(Integer articleId, Integer afterVersion, int limit) {
        if (articleId == null) {
            throw new IllegalArgumentException("articleId must not be null");
        }
        return contentAssetDao.listVersions(articleId, afterVersion, Math.min(limit, 51));
    }

    public List<ContentAsset> listCleanupCandidates(int limit) {
        return contentAssetDao.listCleanupCandidates(Math.max(1, Math.min(limit, 100)));
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
