package com.wzh.blog.config;

import com.wzh.blog.media.StorageProviderType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Fails fast on invalid mode combinations and unreplaced production values. */
@Component
public class DeploymentConfigurationValidator {

    private final String profile;
    private final String websiteUrl;
    private final String publicApiUrl;
    private final String allowedOrigins;
    private final StorageProperties storageProperties;
    private final String searchIndexPath;
    private final String monitoringToken;
    private final String cursorSecret;

    public DeploymentConfigurationValidator(
            @Value("${app.deployment-profile:local}") String profile,
            @Value("${website.url}") String websiteUrl,
            @Value("${app.public-api-url}") String publicApiUrl,
            @Value("${app.security.cors.allowed-origins}") String allowedOrigins,
            StorageProperties storageProperties,
            @Value("${app.search.index-path:search-index}") String searchIndexPath,
            @Value("${monitoring.token:}") String monitoringToken,
            @Value("${app.pagination.cursor-secret:}") String cursorSecret) {
        this.profile = profile;
        this.websiteUrl = websiteUrl;
        this.publicApiUrl = publicApiUrl;
        this.allowedOrigins = allowedOrigins;
        this.storageProperties = storageProperties;
        this.searchIndexPath = searchIndexPath;
        this.monitoringToken = monitoringToken;
        this.cursorSecret = cursorSecret;
    }

    @PostConstruct
    void validate() {
        StorageProviderType providerType = StorageProviderType.from(storageProperties.getActiveProvider());
        if (searchIndexPath == null || searchIndexPath.isBlank()) {
            throw new IllegalStateException("app.search.index-path must not be blank");
        }
        if (isProductionLike()) {
            rejectPlaceholder("website.url", websiteUrl);
            rejectPlaceholder("app.public-api-url", publicApiUrl);
            rejectPlaceholder("app.security.cors.allowed-origins", allowedOrigins);
            if (providerType == StorageProviderType.LOCAL) {
                rejectPlaceholder("storage.local-public-url", storageProperties.getLocalPublicUrl());
            } else if (!providerConfigured(providerType)) {
                throw new IllegalStateException("Storage provider is not fully configured: " + providerType.code());
            }
            if (monitoringToken == null || monitoringToken.isBlank() || containsPlaceholder(monitoringToken)) {
                throw new IllegalStateException("monitoring.token must be set for production-like deployments");
            }
            if (cursorSecret == null || cursorSecret.length() < 32 || containsPlaceholder(cursorSecret)) {
                throw new IllegalStateException("app.pagination.cursor-secret must be a strong deployment secret");
            }
        }
    }

    private boolean providerConfigured(StorageProviderType providerType) {
        return switch (providerType) {
            case OSS -> storageProperties.getOss().configured();
            case COS -> storageProperties.getCos().configured();
            case TOS -> storageProperties.getTos().configured();
            case LOCAL -> true;
        };
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
