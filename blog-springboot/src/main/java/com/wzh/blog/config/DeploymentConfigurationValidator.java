package com.wzh.blog.config;

import com.wzh.blog.dao.StorageProviderConfigDao;
import com.wzh.blog.entity.StorageProviderConfig;
import com.wzh.blog.media.StorageProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.List;

/** Fails fast on invalid mode combinations and unreplaced production values. */
@Component
@Order(1)
public class DeploymentConfigurationValidator implements ApplicationRunner {

    private final String profile;
    private final String websiteUrl;
    private final String publicApiUrl;
    private final String allowedOrigins;
    private final String searchIndexPath;
    private final String monitoringToken;
    private final String cursorSecret;
    private final StorageProviderConfigDao storageConfigDao;
    private final StorageConfigCrypto storageConfigCrypto;

    public DeploymentConfigurationValidator(
            @Value("${app.deployment-profile:local}") String profile,
            @Value("${website.url}") String websiteUrl,
            @Value("${app.public-api-url}") String publicApiUrl,
            @Value("${app.security.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.search.index-path:search-index}") String searchIndexPath,
            @Value("${monitoring.token:}") String monitoringToken,
            @Value("${app.pagination.cursor-secret:}") String cursorSecret,
            StorageProviderConfigDao storageConfigDao,
            StorageConfigCrypto storageConfigCrypto) {
        this.profile = profile;
        this.websiteUrl = websiteUrl;
        this.publicApiUrl = publicApiUrl;
        this.allowedOrigins = allowedOrigins;
        this.searchIndexPath = searchIndexPath;
        this.monitoringToken = monitoringToken;
        this.cursorSecret = cursorSecret;
        this.storageConfigDao = storageConfigDao;
        this.storageConfigCrypto = storageConfigCrypto;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (searchIndexPath == null || searchIndexPath.isBlank()) {
            throw new IllegalStateException("app.search.index-path must not be blank");
        }
        if (isProductionLike()) {
            rejectPlaceholder("website.url", websiteUrl);
            rejectPlaceholder("app.public-api-url", publicApiUrl);
            rejectPlaceholder("app.security.cors.allowed-origins", allowedOrigins);
            validateStorageCatalog();
            if (monitoringToken == null || monitoringToken.isBlank() || containsPlaceholder(monitoringToken)) {
                throw new IllegalStateException("monitoring.token must be set for production-like deployments");
            }
            if (cursorSecret == null || cursorSecret.length() < 32 || containsPlaceholder(cursorSecret)) {
                throw new IllegalStateException("app.pagination.cursor-secret must be a strong deployment secret");
            }
        }
    }

    private void validateStorageCatalog() {
        StorageProviderConfig active = storageConfigDao.selectActive();
        if (active == null || !isUsable(active)) {
            throw new IllegalStateException("No usable active storage profile is configured");
        }
        if (!isCloud(active)) {
            rejectProductionPublicUrl("active local storage public URL", active.getPublicUrl());
        }
        List<StorageProviderConfig> catalog = storageConfigDao.selectAll();
        boolean hasCloudProfile = catalog != null && catalog.stream().anyMatch(this::isCloud);
        if ((isCloud(active) || hasCloudProfile) && !storageConfigCrypto.hasKey()) {
            throw new IllegalStateException("Storage configuration encryption is required for cloud profiles");
        }
    }

    private boolean isCloud(StorageProviderConfig profile) {
        if (profile == null || profile.getProvider() == null) {
            return false;
        }
        try {
            return StorageProviderType.from(profile.getProvider()) != StorageProviderType.LOCAL;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isUsable(StorageProviderConfig profile) {
        if (profile == null || profile.getProvider() == null) {
            return false;
        }
        try {
            StorageProviderType provider = StorageProviderType.from(profile.getProvider());
            return provider == StorageProviderType.LOCAL
                    ? hasText(profile.getLocalRoot()) && hasText(profile.getPublicUrl())
                    : hasText(profile.getEndpoint()) && hasText(profile.getRegion()) && hasText(profile.getBucket())
                    && hasText(profile.getPublicUrl()) && hasText(profile.getAccessKeyIdCiphertext())
                    && hasText(profile.getAccessKeySecretCiphertext());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void rejectProductionPublicUrl(String name, String value) {
        rejectPlaceholder(name, value);
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException(name + " must contain a real HTTP(S) URL for " + profile);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must contain a real HTTP(S) URL for " + profile);
        }
    }

    private boolean isProductionLike() {
        String normalized = profile == null ? "" : profile.toLowerCase(Locale.ROOT);
        return normalized.equals("production") || normalized.equals("production-like") || normalized.equals("staging");
    }

    private void rejectPlaceholder(String name, String value) {
        if (value == null || value.isBlank() || containsPlaceholder(value)
                || value.toLowerCase(Locale.ROOT).contains("localhost")) {
            throw new IllegalStateException(name + " must contain a real non-local value for " + profile);
        }
    }

    private boolean containsPlaceholder(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("change-me") || normalized.contains("replace-me")
                || normalized.contains("your-blog") || normalized.contains("example.com");
    }
}
