package com.sentinel.apigateway.repository;

import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryRateLimitRepository implements RateLimitRepository {

    private final Map<String, AtomicLong> windowStartTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    @Override
    public long recordRequest(String apiKey, long windowMs, int maxRequests) {
        long now = System.currentTimeMillis();

        AtomicLong windowStart = windowStartTimes.computeIfAbsent(
                apiKey,
                k -> new AtomicLong(now)
        );
        AtomicInteger count = requestCounts.computeIfAbsent(
                apiKey,
                k -> new AtomicInteger(0)
        );

        long currentWindowStart = windowStart.get();
        if (now - currentWindowStart >= windowMs) {
            windowStart.set(now);
            count.set(0);
        }

        int currentCount = count.get();
        if (currentCount < maxRequests) {
            int newCount = count.incrementAndGet();

            if (newCount <= maxRequests) {
                return 1;
            } else {
                count.decrementAndGet();
                return 0;
            }
        } else {
            return 0;
        }
    }

    @Override
    public long getCurrentCount(String apiKey, long windowMs) {
        AtomicLong windowStart = windowStartTimes.get(apiKey);
        if (windowStart == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        if (now - windowStart.get() >= windowMs) {
            return 0;
        }

        AtomicInteger count = requestCounts.get(apiKey);
        return count != null ? count.get() : 0;
    }
}