package com.wzh.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the read-only DMIT Xray traffic bridge. */
@Data
@ConfigurationProperties(prefix = "app.xray-traffic")
public class XrayTrafficProperties {

    private boolean enabled;
    private String baseUrl = "";
    private String sharedSecret = "";
    private long maxSkewSeconds = 90;
}
