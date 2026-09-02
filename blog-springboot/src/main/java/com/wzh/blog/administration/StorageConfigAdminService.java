package com.wzh.blog.administration;

import com.wzh.blog.config.StorageConfigCrypto;
import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.exception.ConflictException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.infrastructure.storage.StorageProviderFactory;
import com.wzh.blog.media.StorageProvider;
import com.wzh.blog.media.StorageProviderRegistry;
import com.wzh.blog.media.StorageProviderType;
import com.wzh.blog.media.StorageUsage;
import com.wzh.blog.vo.StorageConfigListResponse;
import com.wzh.blog.vo.StorageConfigRequest;
import com.wzh.blog.vo.StorageConfigSummaryVO;
import com.wzh.blog.vo.StorageProviderSelectionResponse;
import com.wzh.blog.vo.StorageProviderStatusVO;
import com.wzh.blog.vo.StorageProviderValidationVO;
import com.wzh.blog.vo.StorageUsageVO;
import com.wzh.blog.vo.StorageValidationVO;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Safe controller-to-profile-DAO administration boundary. */
@Service
@Log4j2
public class StorageConfigAdminService {

    private static final String VALIDATION_SUCCESS = "验证成功";
    private static final String VALIDATION_FAILURE = "配置验证失败";
    private static final String USAGE_FAILURE = "使用量刷新失败";

    private final StorageProviderConfigDao configDao;
    private final StorageProviderRegistry registry;
    private final StorageConfigCrypto crypto;
    private final StorageProviderFactory providerFactory;
    private final TransactionTemplate transactionTemplate;

    public StorageConfigAdminService(StorageProviderConfigDao configDao,
                                     StorageProviderRegistry registry,
                                     StorageConfigCrypto crypto) {
        this(configDao, registry, crypto, new StorageProviderFactory(crypto), null);
    }

    @Autowired
    public StorageConfigAdminService(StorageProviderConfigDao configDao,
                                     StorageProviderRegistry registry,
                                     StorageConfigCrypto crypto,
                                     StorageProviderFactory providerFactory,
                                     PlatformTransactionManager transactionManager) {
        this.configDao = Objects.requireNonNull(configDao, "configDao");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.providerFactory = Objects.requireNonNull(providerFactory, "providerFactory");
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    /** Keeps direct construction compatible with callers that provide only a transaction manager. */
    public StorageConfigAdminService(StorageProviderConfigDao configDao,
                                     StorageProviderRegistry registry,
                                     StorageConfigCrypto crypto,
                                     PlatformTransactionManager transactionManager) {
        this(configDao, registry, crypto, new StorageProviderFactory(crypto), transactionManager);
    }

    public StorageConfigListResponse list() {
        List<StorageConfigSummaryVO> configs = configDao.selectAll().stream().map(this::summary).toList();
        Long activeConfigId = configs.stream().filter(StorageConfigSummaryVO::active)
                .map(StorageConfigSummaryVO::id).findFirst().orElse(null);
        return new StorageConfigListResponse(activeConfigId, configs);
    }

    public StorageConfigSummaryVO create(StorageConfigRequest request, Integer userId) {
        StorageProviderConfig config = toConfig(request, null, userId);
        config.setActive(false);
        config.setConfigSource("ADMIN");
        config.setLastValidationStatus("NEVER");
        config.setUsageStatus("NEVER");
        config.setCreatedAt(LocalDateTime.now());
        configDao.insertProfile(config);
        return summary(config);
    }

    public StorageConfigSummaryVO update(Long id, StorageConfigRequest request, Integer userId) {
        StorageProviderConfig existing = requireConfig(id);
        StorageProviderConfig updated = toConfig(request, existing, userId);
        updated.setId(existing.getId());
        updated.setActive(existing.getActive());
        updated.setConfigSource("ADMIN");
        updated.setCreatedAt(existing.getCreatedAt());

        if (Boolean.TRUE.equals(existing.getActive())) {
            StorageValidationVO validation = validateProfile(updated, false);
            if (!validation.success()) {
                throw new ConflictException(VALIDATION_FAILURE);
            }
            applyValidation(updated, validation);
        } else {
            clearValidation(updated);
        }
        clearUsage(updated);

        if (configDao.updateProfile(updated) != 1) {
            throw new ConflictException("存储配置更新失败");
        }
        registry.invalidate(id);
        return summary(updated);
    }

    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("存储配置 ID 不能为空");
        }
        if (transactionTemplate == null) {
            deleteLocked(id);
        } else {
            transactionTemplate.executeWithoutResult(status -> deleteLocked(id));
        }
        registry.invalidate(id);
    }

    public StorageValidationVO validate(Long id) {
        StorageProviderConfig config = requireConfig(id);
        return validateProfile(config, true);
    }

    public StorageConfigSummaryVO activate(Long id, Integer userId) {
        StorageProviderConfig candidate = requireConfig(id);
        StorageValidationVO validation = validateProfile(candidate, false);
        if (!validation.success()) {
            throw new ConflictException(VALIDATION_FAILURE);
        }
        StorageProviderConfig activated = transactionTemplate == null
                ? activateLocked(candidate, validation, userId)
                : transactionTemplate.execute(status -> activateLocked(candidate, validation, userId));
        registry.refresh(id);
        activated.setActive(true);
        applyValidation(activated, validation);
        return summary(activated);
    }

    public StorageUsageVO refreshUsage(Long id) {
        StorageProviderConfig config = requireConfig(id);
        LocalDateTime checkedAt = LocalDateTime.now();
        try {
            StorageUsage usage = registry.providerForConfig(id).usage();
            LocalDateTime latestModified = toLocalDateTime(usage.latestObjectModified());
            configDao.updateUsage(id, "SUCCESS", usage.objectCount(), usage.totalBytes(), latestModified,
                    checkedAt, null);
            return new StorageUsageVO("SUCCESS", usage.objectCount(), usage.totalBytes(), latestModified,
                    checkedAt, null);
        } catch (Exception exception) {
            logProviderFailure("usage refresh", id, exception);
            configDao.updateUsage(id, "FAILED", config.getUsageObjectCount(), config.getUsageBytes(),
                    config.getUsageLastModified(), checkedAt, USAGE_FAILURE);
            return new StorageUsageVO("FAILED", config.getUsageObjectCount(), config.getUsageBytes(),
                    config.getUsageLastModified(), checkedAt, USAGE_FAILURE);
        }
    }

    /** Deprecated provider-only read adapter. */
    @Deprecated
    public StorageProviderSelectionResponse current() {
        StorageProviderConfig active = configDao.selectActive();
        if (active == null) {
            return new StorageProviderSelectionResponse(null, null, supportedProviderCodes());
        }
        return new StorageProviderSelectionResponse(active.getProvider(), active.getId(), supportedProviderCodes());
    }

    /** Deprecated provider-only aggregate read adapter. */
    @Deprecated
    public List<StorageProviderStatusVO> providers() {
        List<StorageProviderConfig> configs = configDao.selectAll();
        return Arrays.stream(StorageProviderType.values()).map(type -> {
            List<StorageProviderConfig> sameType = configs.stream()
                    .filter(config -> type.code().equalsIgnoreCase(config.getProvider())).toList();
            int usable = (int) sameType.stream().filter(this::isConfigured).count();
            boolean active = sameType.stream().anyMatch(config -> Boolean.TRUE.equals(config.getActive()));
            return new StorageProviderStatusVO(type.code(), active, usable > 0, usable > 0, true, usable);
        }).toList();
    }

    /** Deprecated provider-only validation adapter. */
    @Deprecated
    public StorageProviderValidationVO validateProvider(String providerCode) {
        StorageProviderConfig config = uniqueUsableProfile(providerCode);
        StorageValidationVO result = validateProfile(config, true);
        return new StorageProviderValidationVO(config.getProvider(), config.getId(), result.success(),
                result.success(), result.success(), result.success(), result.message());
    }

    /** Deprecated provider-only switch adapter. */
    @Deprecated
    public StorageProviderSelectionResponse switchProvider(String providerCode, Integer userId) {
        StorageProviderConfig config = uniqueUsableProfile(providerCode);
        activate(config.getId(), userId);
        return current();
    }

    private StorageProviderConfig activateLocked(StorageProviderConfig candidate,
                                                 StorageValidationVO validation,
                                                 Integer userId) {
        StorageProviderConfig target = configDao.selectByIdForUpdate(candidate.getId());
        if (target == null) {
            throw new NotFoundException("存储配置不存在");
        }
        // Lock the active row as well, so the subsequent activateOnly update is
        // serialized with competing profile switches.
        configDao.selectActiveForUpdate();
        if (!sameProfile(candidate, target)) {
            throw new ConflictException("存储配置已被修改，请重新验证后再激活");
        }
        configDao.updateValidation(target.getId(), validation.status(), validation.validatedAt(), validation.message());
        applyValidation(target, validation);
        configDao.activateOnly(target.getId(), userId, LocalDateTime.now());
        return target;
    }

    private void deleteLocked(Long id) {
        StorageProviderConfig target = configDao.selectByIdForUpdate(id);
        if (target == null) {
            throw new NotFoundException("存储配置不存在");
        }
        StorageProviderConfig active = configDao.selectActiveForUpdate();
        if (Boolean.TRUE.equals(target.getActive()) || active != null && id.equals(active.getId())) {
            throw new ConflictException("当前启用的存储配置不能删除");
        }
        if (configDao.countAssetReferences(id) > 0) {
            throw new ConflictException("存储配置仍被资产引用，不能删除");
        }
        try {
            if (configDao.deleteById(id) != 1) {
                throw new NotFoundException("存储配置不存在");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("存储配置仍被资产引用，不能删除");
        }
    }

    private StorageValidationVO validateProfile(StorageProviderConfig config, boolean persist) {
        LocalDateTime validatedAt = LocalDateTime.now();
        String status = "FAILED";
        String message = VALIDATION_FAILURE;
        boolean success = false;
        StorageProvider provider = null;
        try {
            if (!isConfigured(config)) {
                message = "配置字段不完整";
            } else {
                // Candidate profiles must never enter the registry cache before they
                // are persisted. The factory also owns the credential decryption
                // boundary for this short-lived validation provider.
                provider = providerFactory.create(config);
                provider.validateConnection();
                status = "SUCCESS";
                message = VALIDATION_SUCCESS;
                success = true;
            }
        } catch (Exception ignored) {
            logProviderFailure("validation", config.getId(), ignored);
        } finally {
            if (provider != null) {
                try {
                    provider.close();
                } catch (Exception ignored) {
                    logProviderFailure("validation provider close", config.getId(), ignored);
                }
            }
        }
        if (persist) {
            configDao.updateValidation(config.getId(), status, validatedAt, message);
        }
        return new StorageValidationVO(status, success, validatedAt, message);
    }

    private void applyValidation(StorageProviderConfig config, StorageValidationVO validation) {
        config.setLastValidationStatus(validation.status());
        config.setLastValidationAt(validation.validatedAt());
        config.setLastValidationMessage(validation.message());
    }

    private void clearValidation(StorageProviderConfig config) {
        config.setLastValidationStatus("NEVER");
        config.setLastValidationAt(null);
        config.setLastValidationMessage(null);
    }

    private void clearUsage(StorageProviderConfig config) {
        config.setUsageStatus("NEVER");
        config.setUsageObjectCount(null);
        config.setUsageBytes(null);
        config.setUsageLastModified(null);
        config.setUsageCheckedAt(null);
        config.setUsageError(null);
    }

    private boolean sameProfile(StorageProviderConfig expected, StorageProviderConfig actual) {
        return Objects.equals(expected.getId(), actual.getId())
                && Objects.equals(expected.getConfigName(), actual.getConfigName())
                && Objects.equals(expected.getProvider(), actual.getProvider())
                && Objects.equals(expected.getEndpoint(), actual.getEndpoint())
                && Objects.equals(expected.getBucket(), actual.getBucket())
                && Objects.equals(expected.getRegion(), actual.getRegion())
                && Objects.equals(expected.getLocalRoot(), actual.getLocalRoot())
                && Objects.equals(expected.getPublicUrl(), actual.getPublicUrl())
                && Objects.equals(expected.getAccessKeyIdCiphertext(), actual.getAccessKeyIdCiphertext())
                && Objects.equals(expected.getAccessKeySecretCiphertext(), actual.getAccessKeySecretCiphertext())
                && Objects.equals(expected.getActive(), actual.getActive())
                && Objects.equals(expected.getConfigSource(), actual.getConfigSource())
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt())
                && Objects.equals(expected.getUpdatedBy(), actual.getUpdatedBy());
    }

    private StorageProviderConfig toConfig(StorageConfigRequest request, StorageProviderConfig existing,
                                           Integer userId) {
        if (request == null) {
            throw new IllegalArgumentException("存储配置不能为空");
        }
        String name = required(request.name(), "配置名称不能为空");
        StorageProviderType provider = provider(request.provider());
        String publicUrl = safeHttpUrl(request.publicUrl(), "公共地址不合法");
        LocalDateTime now = LocalDateTime.now();
        StorageProviderConfig.StorageProviderConfigBuilder builder = StorageProviderConfig.builder()
                .configName(name).provider(provider.code()).publicUrl(publicUrl).updatedAt(now).updatedBy(userId);
        if (provider == StorageProviderType.LOCAL) {
            builder.localRoot(normalizedRoot(request.localRoot()));
            return builder.accessKeyIdCiphertext(null).accessKeySecretCiphertext(null).build();
        }
        String accessKeyId = preserveOrEncrypt(request.accessKeyId(), existing == null ? null
                : existing.getAccessKeyIdCiphertext());
        String accessKeySecret = preserveOrEncrypt(request.accessKeySecret(), existing == null ? null
                : existing.getAccessKeySecretCiphertext());
        if (isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            throw new IllegalArgumentException("云存储凭据不能为空");
        }
        return builder.endpoint(safeHttpUrl(request.endpoint(), "Endpoint 地址不合法"))
                .region(required(request.region(), "Region 不能为空"))
                .bucket(required(request.bucket(), "Bucket 不能为空"))
                .localRoot(null).accessKeyIdCiphertext(accessKeyId)
                .accessKeySecretCiphertext(accessKeySecret).build();
    }

    private StorageConfigSummaryVO summary(StorageProviderConfig config) {
        return new StorageConfigSummaryVO(config.getId(), config.getConfigName(), config.getProvider(),
                Boolean.TRUE.equals(config.getActive()), isConfigured(config), credentialsConfigured(config),
                config.getEndpoint(), config.getRegion(), config.getBucket(), config.getLocalRoot(), config.getPublicUrl(),
                new StorageValidationVO(defaultStatus(config.getLastValidationStatus()),
                        "SUCCESS".equals(config.getLastValidationStatus()), config.getLastValidationAt(),
                        safeValidationMessage(config.getLastValidationStatus(), config.getLastValidationMessage())),
                new StorageUsageVO(defaultStatus(config.getUsageStatus()), config.getUsageObjectCount(),
                        config.getUsageBytes(), config.getUsageLastModified(), config.getUsageCheckedAt(),
                        safeUsageError(config.getUsageStatus(), config.getUsageError())));
    }

    private StorageProviderConfig requireConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("存储配置 ID 不能为空");
        }
        StorageProviderConfig config = configDao.selectById(id);
        if (config == null) {
            throw new NotFoundException("存储配置不存在");
        }
        return config;
    }

    private StorageProviderConfig uniqueUsableProfile(String providerCode) {
        StorageProviderType type = provider(providerCode);
        List<StorageProviderConfig> matches = configDao.selectAll().stream()
                .filter(config -> type.code().equalsIgnoreCase(config.getProvider())).toList();
        if (matches.size() != 1) {
            throw new ConflictException("该 provider 无法唯一确定配置，请使用配置 ID");
        }
        return matches.getFirst();
    }

    private void logProviderFailure(String operation, Long configId, Exception exception) {
        log.warn("Storage {} failed: {}", operation, safeProviderFailureDiagnostic(configId, exception));
    }

    static String safeProviderFailureDiagnostic(Long configId, Exception exception) {
        String type = exception == null ? "Unknown" : exception.getClass().getSimpleName();
        return "configId=" + configId + ", exceptionType=" + type;
    }

    private List<String> supportedProviderCodes() {
        return Arrays.stream(StorageProviderType.values()).map(StorageProviderType::code).toList();
    }

    private boolean isConfigured(StorageProviderConfig config) {
        if (config == null) {
            return false;
        }
        try {
            StorageProviderType type = provider(config.getProvider());
            return type == StorageProviderType.LOCAL
                    ? !isBlank(config.getLocalRoot()) && isSafeHttpUrl(config.getPublicUrl())
                    : !isBlank(config.getEndpoint()) && isSafeHttpUrl(config.getEndpoint())
                    && !isBlank(config.getRegion()) && !isBlank(config.getBucket())
                    && isSafeHttpUrl(config.getPublicUrl()) && credentialsConfigured(config);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean credentialsConfigured(StorageProviderConfig config) {
        try {
            return provider(config.getProvider()) == StorageProviderType.LOCAL
                    || (!isBlank(config.getAccessKeyIdCiphertext()) && !isBlank(config.getAccessKeySecretCiphertext()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String preserveOrEncrypt(String supplied, String existingCiphertext) {
        return isBlank(supplied) ? existingCiphertext : crypto.encrypt(supplied.trim());
    }

    private StorageProviderType provider(String value) {
        try {
            return StorageProviderType.from(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的存储 provider");
        }
    }

    private String normalizedRoot(String value) {
        String root = required(value, "本地根目录不能为空");
        try {
            Path input = Path.of(root);
            if (!input.isAbsolute()) {
                throw new IllegalArgumentException("本地根目录必须是绝对路径");
            }
            Path path = input.normalize();
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("本地根目录必须是绝对路径");
            }
            return path.toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("本地根目录不合法");
        }
    }

    private String safeHttpUrl(String value, String message) {
        if (!isSafeHttpUrl(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean isSafeHttpUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme())) && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultStatus(String value) {
        return isBlank(value) ? "NEVER" : value;
    }

    private String safeValidationMessage(String status, String message) {
        return "SUCCESS".equals(status) ? VALIDATION_SUCCESS : "FAILED".equals(status) ? VALIDATION_FAILURE : null;
    }

    private String safeUsageError(String status, String error) {
        return "FAILED".equals(status) ? USAGE_FAILURE : null;
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
