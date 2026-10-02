package com.wherefood.config;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class SharedRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public SharedRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean allow(String policy, String identity, int limit, Duration window) {
        String key = "whatplan:ratelimit:v1:" + policy + ":" + sha256(identity);
        long ttlMillis = Math.max(1, window.toMillis());
        Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(ttlMillis));
        if (count == null) throw new IllegalStateException("Redis rate limit script returned no counter");
        return count <= limit;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
