package com.wzh.blog.strategy.context;

import com.wzh.blog.media.MediaAssetLedger;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.security.UploadValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Provider-neutral media upload context. Exactly one provider handles new uploads. */
@Service
public class UploadStrategyContext implements MediaAssetStore {

    private static final DateTimeFormatter OBJECT_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final StorageProviderRegistry registry;
    private final MediaAssetLedger assetLedger;
    private final UploadValidationService validationService;

    public UploadStrategyContext(StorageProviderRegistry registry,
                                 MediaAssetLedger assetLedger,
                                 UploadValidationService validationService) {
        this.registry = registry;
        this.assetLedger = assetLedger;
        this.validationService = validationService;
    }

    @Override
    public String upload(MultipartFile file, String path) {
        validationService.validate(file, path);
        String extension = validationService.extension(file).toLowerCase(Locale.ROOT);
        String objectKey = "media/" + LocalDate.now().format(OBJECT_MONTH_FORMAT)
                + "/" + UUID.randomUUID() + extension;
        ObjectKeyPolicy.requireSafe(objectKey);
        Long storageConfigId = registry.activeConfigId();
        StorageProvider provider = registry.providerForConfig(storageConfigId);
        try {
            try (InputStream input = file.getInputStream()) {
                provider.put(objectKey, input, file.getSize(), contentType(extension));
            }
            String reference = registry.publicReference(storageConfigId, objectKey);
            assetLedger.register(reference, objectKey, provider.type().code(), storageConfigId);
            return reference;
        } catch (IOException exception) {
            cleanupFailedUpload(provider, objectKey, exception);
            throw new IllegalStateException("文件上传失败", exception);
        } catch (RuntimeException exception) {
            cleanupFailedUpload(provider, objectKey, exception);
            throw exception;
        }
    }

    @Override
    public void delete(String fileReference) {
        MediaAssetLedger.MediaAssetLocation location = assetLedger.locationFor(fileReference);
        StorageProvider provider = resolveProvider(location);
        String objectKey = location == null || location.objectKey() == null
                ? toObjectKey(fileReference, location, provider.type())
                : location.objectKey();
        try {
            provider.delete(objectKey);
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    @Override
    public boolean exists(String fileReference) {
        MediaAssetLedger.MediaAssetLocation location = assetLedger.locationFor(fileReference);
        StorageProvider provider = resolveProvider(location);
        String objectKey = location == null || location.objectKey() == null
                ? toObjectKey(fileReference, location, provider.type())
                : location.objectKey();
        try {
            return provider.exists(objectKey);
        } catch (IOException exception) {
            throw new IllegalStateException("文件状态读取失败", exception);
        }
    }

    private String toObjectKey(String fileReference,
                               MediaAssetLedger.MediaAssetLocation location,
                               StorageProviderType providerType) {
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("文件引用不能为空");
        }
        String reference = fileReference.replace('\\', '/');
        String prefix = location != null && location.storageConfigId() != null
                ? registry.publicBase(location.storageConfigId())
                : registry.publicBase(providerType);
        String objectKey = reference.startsWith(prefix)
                ? reference.substring(prefix.length()) : reference;
        return ObjectKeyPolicy.requireSafe(objectKey.startsWith("/") ? objectKey.substring(1) : objectKey);
    }

    private StorageProvider resolveProvider(MediaAssetLedger.MediaAssetLocation location) {
        if (location != null && location.storageConfigId() != null) {
            return registry.providerForConfig(location.storageConfigId());
        }
        if (location == null || location.provider() == null || location.provider().isBlank()) {
            return registry.providerFor(registry.activeProviderType());
        }
        try {
            return registry.providerForLegacyProvider(StorageProviderType.from(location.provider()));
        } catch (IllegalArgumentException ignored) {
            return registry.providerFor(registry.activeProviderType());
        }
    }

    private String contentType(String extension) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    private void cleanupFailedUpload(StorageProvider provider, String objectKey, Exception failure) {
        try {
            // Object keys are unique and provider delete is an idempotent
            // operation, so this is safe even when the upload failed halfway.
            provider.delete(objectKey);
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
