package com.wzh.blog.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Prevents Spring Boot's classpath-driven Redis auto-configuration from
 * creating a connection factory when Redis is explicitly disabled.
 *
 * The Redis server remains optional infrastructure so the same artifact can
 * run in both modes. The client jars are bundled because the enabled mode
 * uses Redis types from application code; the explicit application switch,
 * rather than the mere presence of those jars, controls connection/session/
 * listener creation.
 */
public final class RedisAutoConfigurationEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "ticastrRedisOptionalAutoConfiguration";

    private static final Set<String> REDIS_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.health.DataRedisReactiveHealthContributorAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.observation.LettuceObservationAutoConfiguration",
            "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Boolean.parseBoolean(environment.getProperty("app.redis.enabled", "false"))) {
            return;
        }
        Set<String> exclusions = new LinkedHashSet<>();
        addExclusions(exclusions, environment.getProperty("spring.autoconfigure.exclude", ""));
        exclusions.addAll(REDIS_AUTO_CONFIGURATIONS);
        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                java.util.Map.of("spring.autoconfigure.exclude", String.join(",", exclusions))));
    }

    private void addExclusions(Set<String> exclusions, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        for (String value : configured.split(",")) {
            String normalized = value.trim();
            if (!normalized.isBlank()) {
                exclusions.add(normalized);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
