package com.wherefood.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class SharedRateLimiterIntegrationTest {
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test-secret");
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        config.setPassword(RedisPassword.of("test-secret"));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void sharesCounterAcrossLimiterInstancesAndAppliesLimit() {
        SharedRateLimiter firstReplica = new SharedRateLimiter(template);
        SharedRateLimiter secondReplica = new SharedRateLimiter(template);
        String identity = "user:" + java.util.UUID.randomUUID();

        assertTrue(firstReplica.allow("test", identity, 2, Duration.ofMinutes(1)));
        assertTrue(secondReplica.allow("test", identity, 2, Duration.ofMinutes(1)));
        assertFalse(firstReplica.allow("test", identity, 2, Duration.ofMinutes(1)));
    }
}
