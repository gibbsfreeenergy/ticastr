package com.wzh.blog.strategy.context;

import com.wzh.blog.media.MediaAssetLedger;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.media.ObjectKeyPolicy;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.util.FileUtils;
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
        StorageProvider provider = registry.providerForNewAsset();
        try {
            try (InputStream input = file.getInputStream()) {
                provider.put(objectKey, input, file.getSize(), contentType(extension));
            }
            String reference = registry.publicReference(provider.type(), objectKey);
            assetLedger.register(reference, objectKey, provider.type().code());
            return reference;
        } catch (IOException exception) {
            cleanupFailedUpload(provider, objectKey, exception);
            throw new IllegalStateException("文件上传失败", exception);
        } catch (RuntimeException exception) {
            cleanupFailedUpload(provider, objectKey, exception);
            throw exception;
        }
    }

    /** Compatibility name for controllers that have not yet migrated. */
    public String executeUploadStrategy(MultipartFile file, String path) {
        return upload(file, path);
    }

    @Override
    public void delete(String fileReference) {
        MediaAssetLedger.MediaAssetLocation location = assetLedger.locationFor(fileReference);
        StorageProviderType providerType = providerType(location);
        String objectKey = location == null || location.objectKey() == null
                ? toObjectKey(fileReference, providerType)
                : location.objectKey();
        try {
            registry.providerFor(providerType).delete(objectKey);
        } catch (IOException exception) {
            throw new IllegalStateException("文件删除失败", exception);
        }
    }

    /** Compatibility name for the old strategy context API. */
    public void deleteFile(String filePath) {
        delete(filePath);
    }

    @Override
    public boolean exists(String fileReference) {
        MediaAssetLedger.MediaAssetLocation location = assetLedger.locationFor(fileReference);
        StorageProviderType providerType = providerType(location);
        String objectKey = location == null || location.objectKey() == null
                ? toObjectKey(fileReference, providerType)
                : location.objectKey();
        try {
            return registry.providerFor(providerType).exists(objectKey);
        } catch (IOException exception) {
            throw new IllegalStateException("文件状态读取失败", exception);
        }
    }

    private String toObjectKey(String fileReference, StorageProviderType providerType) {
        if (fileReference == null || fileReference.isBlank()) {
            throw new IllegalArgumentException("文件引用不能为空");
        }
        String reference = fileReference.replace('\\', '/');
        String prefix = registry.publicBase(providerType);
        String objectKey = reference.startsWith(prefix)
                ? reference.substring(prefix.length()) : reference;
        return ObjectKeyPolicy.requireSafe(objectKey.startsWith("/") ? objectKey.substring(1) : objectKey);
    }

    private StorageProviderType providerType(MediaAssetLedger.MediaAssetLocation location) {
        if (location == null || location.provider() == null || location.provider().isBlank()) {
            return registry.activeProviderType();
        }
        try {
            return StorageProviderType.from(location.provider());
        } catch (IllegalArgumentException ignored) {
            return registry.activeProviderType();
        }
    }

    private String contentType(String extension) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".wav" -> "audio/wav";
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
