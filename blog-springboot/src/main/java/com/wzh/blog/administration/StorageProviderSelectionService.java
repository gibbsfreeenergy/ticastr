package com.wzh.blog.administration;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.exception.ConflictException;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.vo.StorageProviderSelectionResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Single active provider selector shared by article content and media uploads. */
@Log4j2
public class StorageProviderSelectionService {

    private final StorageProviderConfigDao configDao;
    private final StorageProviderRegistry registry;
    private final TransactionTemplate transactionTemplate;

    public StorageProviderSelectionService(StorageProviderConfigDao configDao,
                                           StorageProviderRegistry registry) {
        this(configDao, registry, null);
    }

    @Autowired
    public StorageProviderSelectionService(StorageProviderConfigDao configDao,
                                           StorageProviderRegistry registry,
                                           org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.configDao = configDao;
        this.registry = registry;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    void loadPersistedSelection() {
        StorageProviderConfig config = configDao.selectSingleton();
        if (config == null || config.getActiveProvider() == null) {
            return;
        }
        registry.refresh(StorageProviderType.from(config.getActiveProvider()));
    }

    public StorageProviderSelectionResponse current() {
        return new StorageProviderSelectionResponse(
                registry.activeProviderType().code(),
                Arrays.stream(StorageProviderType.values()).map(StorageProviderType::code).toList());
    }

    public synchronized StorageProviderSelectionResponse switchProvider(String providerCode, Integer userId) {
        StorageProviderType provider = StorageProviderType.from(providerCode);
        if (provider == registry.activeProviderType()) {
            return current();
        }
        // Explicit validation is the only place a provider adapter is allowed
        // to perform network I/O. Inactive providers remain lazy at startup.
        try {
            validateForSwitch(provider);
        } catch (RuntimeException exception) {
            throw new ConflictException("存储 provider 连接验证失败: " + provider.code());
        }
        Runnable persist = () -> {
            if (configDao.updateActive(provider.code(), userId, LocalDateTime.now()) != 1) {
                throw new ConflictException("存储 provider 配置更新失败");
            }
        };
        if (transactionTemplate == null) {
            persist.run();
        } else {
            transactionTemplate.executeWithoutResult(status -> persist.run());
        }
        registry.refresh(provider);
        log.info("Active storage provider switched to {} by user {}", provider.code(), userId);
        return current();
    }

    protected void validateForSwitch(StorageProviderType provider) {
        registry.providerFor(provider).validateConnection();
    }
}
