package com.wzh.blog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers Xray traffic bridge settings without enabling the feature by default. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(XrayTrafficProperties.class)
public class XrayTrafficConfiguration {
}
