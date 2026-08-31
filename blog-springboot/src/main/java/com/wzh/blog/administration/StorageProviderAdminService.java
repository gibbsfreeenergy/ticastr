package com.wzh.blog.administration;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.media.StorageObjectMetadata;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.vo.StorageProviderStatusVO;
import com.wzh.blog.vo.StorageProviderValidationVO;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Canonical administration boundary for selecting the one active object provider. */
@Service
public class StorageProviderAdminService extends StorageProviderSelectionService {

    private final StorageProviderRegistry registry;

    public StorageProviderAdminService(StorageProviderConfigDao configDao,
                                       StorageProviderRegistry registry,
                                       org.springframework.transaction.PlatformTransactionManager transactionManager) {
        super(configDao, registry, transactionManager);
        this.registry = registry;
    }

    public List<StorageProviderStatusVO> providers() {
        StorageProviderType active = registry.activeProviderType();
        return Arrays.stream(StorageProviderType.values())
                .map(type -> {
                    StorageProvider provider = registry.providerFor(type);
                    return new StorageProviderStatusVO(type.code(), type == active,
                            provider.configured(), provider.configured(), true);
                })
                .toList();
    }

    public StorageProviderValidationVO validateProvider(String providerCode) {
        StorageProviderType type = StorageProviderType.from(providerCode);
        StorageProvider provider = registry.providerFor(type);
        if (!provider.configured()) {
            return new StorageProviderValidationVO(type.code(), false, false, false, false, "provider未完成配置");
        }
        String objectKey = ".health/admin/" + UUID.randomUUID() + ".txt";
        byte[] body = "ticastr-storage-health".getBytes(StandardCharsets.UTF_8);
        boolean write = false;
        boolean read = false;
        boolean deleted = false;
        try {
            StorageObjectMetadata stored = provider.put(objectKey,
                    new ByteArrayInputStream(body), body.length, "text/plain");
            write = stored != null && stored.sizeBytes() == body.length;
            StorageObjectMetadata headed = provider.head(objectKey);
            try (StorageObject object = provider.get(objectKey)) {
                read = headed != null
                        && headed.sizeBytes() == body.length
                        && Arrays.equals(body, object.content().readAllBytes());
            }
            provider.delete(objectKey);
            deleted = !provider.exists(objectKey);
            return new StorageProviderValidationVO(type.code(), write && read && deleted,
                    write, read, deleted, write && read && deleted ? "验证成功" : "读写删除检查未全部通过");
        } catch (Exception exception) {
            return new StorageProviderValidationVO(type.code(), false, write, read, deleted, "provider验证失败");
        } finally {
            if (!deleted) {
                try {
                    provider.delete(objectKey);
                } catch (Exception ignored) {
                    // The safe result intentionally hides provider internals.
                }
            }
        }
    }

    @Override
    protected void validateForSwitch(StorageProviderType provider) {
        StorageProviderValidationVO result = validateProvider(provider.code());
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
    }
}
