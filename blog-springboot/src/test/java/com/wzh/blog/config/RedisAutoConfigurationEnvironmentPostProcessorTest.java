package com.wzh.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAutoConfigurationEnvironmentPostProcessorTest {

    @Test
    void excludesClasspathDrivenRedisConfigurationWhenFeatureIsOff() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.redis.enabled", "false")
                .withProperty("spring.autoconfigure.exclude", "example.ExistingAutoConfiguration");

        new RedisAutoConfigurationEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(com.wzh.blog.BlogApplication.class));

        String exclusions = environment.getProperty("spring.autoconfigure.exclude");
        assertThat(exclusions).contains("example.ExistingAutoConfiguration")
                .contains("org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
                .contains("org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration");
    }

    @Test
    void leavesRedisAutoConfigurationAloneWhenFeatureIsOn() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.redis.enabled", "true");

        new RedisAutoConfigurationEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(com.wzh.blog.BlogApplication.class));

        assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
    }
}
