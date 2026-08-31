package com.wzh.blog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Feature switches for optional Redis capabilities. */
@ConfigurationProperties(prefix = "app.redis")
public class RedisFeatureProperties {

    private boolean enabled;
    private String keyPrefix = "ticastr";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
