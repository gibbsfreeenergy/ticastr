package com.wzh.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Settings for the local, rebuildable article search index. */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    private String dataRoot = "./data";
    /** Relative paths are resolved below dataRoot; absolute paths must remain below it. */
    private String indexPath = "search-index";
    private int maxQueryBytes = 100;
    private int maxResults = 20;
}
