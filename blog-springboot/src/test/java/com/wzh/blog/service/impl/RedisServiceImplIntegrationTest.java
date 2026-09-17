package com.wzh.blog.service.impl;

import com.wzh.blog.config.RedisConfig;
import com.wzh.blog.infrastructure.redis.RedisLockStore;
import com.wzh.blog.service.DistributedLockService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisServiceImplIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static RedisServiceImpl redisService;
    private static DistributedLockService lockService;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
        redisService = new RedisServiceImpl(redisTemplate);
        lockService = new DistributedLockService(new RedisLockStore(new StringRedisTemplate(connectionFactory)));
    }

    @AfterAll
    static void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void storesAndDeletesCacheEntries() {
        redisService.set("test:cache", "value", 60);
        assertThat(redisService.get("test:cache")).isEqualTo("value");

        assertThat(redisService.del("test:cache")).isTrue();
        assertThat(redisService.get("test:cache")).isNull();
    }

    @Test
    void releasesOnlyLocksOwnedByTheCaller() {
        String token = lockService.tryLock("test:lock", Duration.ofMinutes(1));
        assertThat(token).isNotNull();
        assertThat(lockService.tryLock("test:lock", Duration.ofMinutes(1))).isNull();

        lockService.release("test:lock", "not-the-owner");
        assertThat(lockService.tryLock("test:lock", Duration.ofMinutes(1))).isNull();

        lockService.release("test:lock", token);
        assertThat(lockService.tryLock("test:lock", Duration.ofMinutes(1))).isNotNull();
    }
}
