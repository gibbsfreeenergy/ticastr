package com.wzh.blog.content;

import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.vo.ArticleContentRequest;
import com.wzh.blog.vo.ArticleContentResponse;
import com.wzh.blog.vo.ArticleContentVersionPage;
import com.wzh.blog.vo.ArticleContentVersionVO;
import com.wzh.blog.web.CursorCodec;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;

/** Coordinates immutable object writes with short, versioned MySQL transactions. */
@Service
@Log4j2
public class ArticleContentServiceImpl implements ArticleContentService {

    private static final String CONTENT_TYPE = "text/markdown; charset=utf-8";
    private static final String FORMAT = "markdown";
    private static final int MAX_CONTENT_BYTES = 1_048_576;

    private final MarkdownSanitizer sanitizer;
    private final StorageProviderRegistry providerRegistry;
    private final ContentAssetPersistenceService persistence;
    private final ContentAssetStore contentAssetStore;
    private final CursorCodec cursorCodec;

    public ArticleContentServiceImpl(MarkdownSanitizer sanitizer,
                                    StorageProviderRegistry providerRegistry,
                                    ContentAssetPersistenceService persistence,
                                    ContentAssetStore contentAssetStore,
                                    CursorCodec cursorCodec) {
        this.sanitizer = sanitizer;
        this.providerRegistry = providerRegistry;
        this.persistence = persistence;
        this.contentAssetStore = contentAssetStore;
        this.cursorCodec = cursorCodec;
    }

    @Override
    public ArticleContentResponse replace(Integer articleId, ArticleContentRequest request) {
        String markdown = sanitizer.sanitize(request.content());
        byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
        StorageProvider provider = providerRegistry.providerForNewAsset();
        ContentAssetReservation reservation = persistence.reserve(
                articleId,
                request.expectedVersion(),
                content.length,
                provider.type().code(),
                CONTENT_TYPE,
                FORMAT);
        ContentAsset asset = reservation.asset();
        StorageObjectMetadata stored;
        try {
            stored = provider.put(asset.getObjectKey(), new ByteArrayInputStream(content), content.length, CONTENT_TYPE);
        } catch (Exception exception) {
            markFailedAndCleanup(provider, asset, exception);
            throw storageFailure("文章内容上传失败", exception);
        }

        try {
            String checksum = canonicalChecksum(stored, content);
            persistence.publish(asset.getAssetId(), articleId,
                    reservation.previousActive() == null ? null : reservation.previousActive().getAssetId(),
                    checksum, content.length);
            asset.setChecksum(checksum);
            asset.setSizeBytes((long) content.length);
            return toResponse(asset, stored);
        } catch (RuntimeException exception) {
            markFailedAndCleanup(provider, asset, exception);
            throw exception;
        }
    }

    @Override
    public ContentAsset currentAsset(Integer articleId) {
        ContentAsset asset = contentAssetStore.findActive(articleId);
        if (asset == null) {
            throw new NotFoundException("文章内容不存在");
        }
        return asset;
    }

    @Override
    public ContentAsset currentPublicAsset(Integer articleId) {
        ContentAsset asset = contentAssetStore.findActivePublic(articleId);
        if (asset == null) {
            throw new NotFoundException("文章内容不存在");
        }
        return asset;
    }

    @Override
    public StorageObject open(Integer articleId) {
        ContentAsset asset = currentAsset(articleId);
        return openAsset(asset);
    }

    @Override
    public StorageObject open(ContentAsset asset) {
        if (asset == null) {
            throw new NotFoundException("文章内容不存在");
        }
        return openAsset(asset);
    }

    @Override
    public StorageObject openPublic(Integer articleId) {
        return openAsset(currentPublicAsset(articleId));
    }

    @Override
    public ArticleContentVersionPage versions(Integer articleId, String cursor, Integer size) {
        int pageSize = size == null ? 20 : size;
        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException("size must be between 1 and 50");
        }
        String fingerprint = cursorCodec.fingerprint("article-content:" + articleId);
        Integer afterVersion = cursor == null ? null : cursorCodec.decodeVersion(cursor, fingerprint);
        List<ContentAsset> rows = persistence.listVersions(articleId, afterVersion, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<ContentAsset> items = hasNext ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasNext && !items.isEmpty()
                ? cursorCodec.encodeVersion(items.get(items.size() - 1).getVersion(), fingerprint)
                : null;
        return new ArticleContentVersionPage(items.stream().map(this::toVersion).toList(), nextCursor, hasNext);
    }

    @Override
    public ArticleContentResponse restore(Integer articleId, Integer version, Integer expectedVersion) {
        ContentAsset source = contentAssetStore.findVersion(articleId, version);
        if (source == null || ContentAssetStatus.DELETED.name().equals(source.getStatus())) {
            throw new NotFoundException("文章内容版本不存在");
        }
        try (StorageObject object = openAsset(source)) {
            byte[] bytes = object.content().readNBytes(MAX_CONTENT_BYTES + 1);
            if (bytes.length > MAX_CONTENT_BYTES) {
                throw new IllegalArgumentException("文章内容不能超过1MiB");
            }
            return replace(articleId, new ArticleContentRequest(
                    new String(bytes, StandardCharsets.UTF_8), expectedVersion));
        } catch (IOException exception) {
            throw storageFailure("文章内容版本读取失败", exception);
        }
    }

    private StorageObject openAsset(ContentAsset asset) {
        StorageProvider provider = providerRegistry.providerFor(StorageProviderType.from(asset.getProvider()));
        try {
            return provider.get(asset.getObjectKey());
        } catch (IOException | RuntimeException exception) {
            throw storageFailure("文章内容读取失败", exception);
        }
    }

    private ArticleContentResponse toResponse(ContentAsset asset, StorageObjectMetadata stored) {
        return new ArticleContentResponse(
                asset.getArticleId(),
                asset.getVersion(),
                asset.getContentType(),
                stored.sizeBytes(),
                asset.getChecksum(),
                stored.lastModified(),
                "/articles/" + asset.getArticleId() + "/content");
    }

    private ArticleContentVersionVO toVersion(ContentAsset asset) {
        return new ArticleContentVersionVO(
                asset.getAssetId(), asset.getArticleId(), asset.getVersion(), asset.getContentType(),
                asset.getSizeBytes(), asset.getChecksum(), asset.getStatus(), asset.getCreatedAt(), asset.getUpdatedAt());
    }

    private String canonicalChecksum(StorageObjectMetadata stored, byte[] content) {
        // All built-in adapters calculate SHA-256 while uploading. The fallback
        // keeps a deterministic checksum if a provider only returns an ETag.
        if (stored.checksum().matches("(?i)[0-9a-f]{64}")) {
            return stored.checksum().toLowerCase(java.util.Locale.ROOT);
        }
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void markFailedAndCleanup(StorageProvider provider, ContentAsset asset, Exception failure) {
        persistence.markFailed(asset.getAssetId(), failure.getMessage());
        try {
            provider.delete(asset.getObjectKey());
            persistence.markDeleted(asset.getAssetId());
        } catch (Exception cleanupFailure) {
            log.error("Unable to clean failed article content asset {}", asset.getAssetId(), cleanupFailure);
            persistence.markDeleteFailed(asset.getAssetId(), cleanupFailure.getMessage());
        }
    }

    private RuntimeException storageFailure(String message, Exception exception) {
        return new IllegalStateException(message, exception);
    }
}
