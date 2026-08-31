package com.wzh.blog.jobs;

import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.content.ContentAssetPersistenceService;
import com.wzh.blog.content.ContentAssetStatus;
import com.wzh.blog.dao.ContentAssetDao;
import com.wzh.blog.dto.DurableEventEnvelope;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Deletes retired Markdown objects using the provider recorded on the asset. */
@Component
public class ContentAssetCleanupHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE = "CONTENT_ASSET_DELETE";

    private final ContentAssetDao contentAssetDao;
    private final ContentAssetPersistenceService persistence;
    private final StorageProviderRegistry providerRegistry;

    public ContentAssetCleanupHandler(ContentAssetDao contentAssetDao,
                                      ContentAssetPersistenceService persistence,
                                      StorageProviderRegistry providerRegistry) {
        this.contentAssetDao = contentAssetDao;
        this.persistence = persistence;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(DurableEventEnvelope<?> event) {
        if (!(event.getPayload() instanceof Map<?, ?> payload)
                || !(payload.get("assetId") instanceof String assetId)
                || assetId.isBlank()) {
            throw new IllegalArgumentException("Invalid content asset delete payload");
        }
        ContentAsset asset = contentAssetDao.selectById(assetId);
        if (asset == null || ContentAssetStatus.DELETED.name().equals(asset.getStatus())
                || ContentAssetStatus.ACTIVE.name().equals(asset.getStatus())) {
            return;
        }
        StorageProvider provider = providerRegistry.providerFor(StorageProviderType.from(asset.getProvider()));
        try {
            provider.delete(asset.getObjectKey());
            persistence.markDeleted(assetId);
        } catch (RuntimeException exception) {
            persistence.markDeleteFailed(assetId, exception.getMessage());
            throw exception;
        } catch (java.io.IOException exception) {
            persistence.markDeleteFailed(assetId, exception.getMessage());
            throw new IllegalStateException("Content asset deletion failed", exception);
        }
    }
}
