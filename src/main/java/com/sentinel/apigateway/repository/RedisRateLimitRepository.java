package com.sentinel.apigateway.repository;

import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.UUID;

@Primary
@Repository
public class RedisRateLimitRepository implements RateLimitRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;
    private static final String KEY_PREFIX = "rl:v1:";
    private final InMemoryRateLimitRepository fallback;

    public RedisRateLimitRepository(StringRedisTemplate redisTemplate,InMemoryRateLimitRepository fallback) {
        this.redisTemplate = redisTemplate;
        this.fallback = fallback;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        redisScript.setResultType(Long.class);
        this.script = redisScript;
    }

    @Override
    public long recordRequest(String apiKey, long windowMs, int maxRequests) {
        String redisKey = KEY_PREFIX + apiKey;
        try {
            Long count = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests),
                    UUID.randomUUID().toString()
            );
            return (count != null) ? count : 0L;
        } catch (Exception e) {
            System.err.println("Redis error, falling back to in-memory: " + e.getMessage());
            return fallback.recordRequest(apiKey, windowMs, maxRequests);
        }
    }

    @Override
    public long getCurrentCount(String apiKey, long windowMs) {
        String redisKey = KEY_PREFIX + apiKey;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - windowMs;

        try {
            Long count = redisTemplate.opsForZSet().count(redisKey, (double) windowStart, (double) currentTime);
            return (count != null) ? count : 0L;
        } catch (Exception e) {
            System.err.println("Redis error, falling back to in-memory: " + e.getMessage());
            return fallback.getCurrentCount(apiKey, windowMs);
        }
    }
}