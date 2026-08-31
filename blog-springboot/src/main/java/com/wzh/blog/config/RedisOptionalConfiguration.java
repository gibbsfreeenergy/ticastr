package com.wzh.blog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers Redis settings without making a Redis server a core dependency. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RedisFeatureProperties.class, RedisStreamProperties.class})
public class RedisOptionalConfiguration {
}
