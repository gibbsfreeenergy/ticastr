package com.wzh.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Provider-neutral storage settings. Secret values are bound from deployment
 * environment variables and are never serialized to API responses.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String activeProvider = "local";
    private String localRoot = "./uploads";
    private String localPublicUrl = "/uploads/";
    private final Provider oss = new Provider();
    private final Provider cos = new Provider();
    private final Provider tos = new Provider();

    @Data
    public static class Provider {
        private String endpoint;
        private String bucket;
        private String region;
        private String publicUrl;
        private String accessKeyId;
        private String accessKeySecret;

        public boolean configured() {
            return hasText(endpoint)
                    && hasText(bucket)
                    && hasText(region)
                    && hasText(publicUrl)
                    && hasText(accessKeyId)
                    && hasText(accessKeySecret);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
