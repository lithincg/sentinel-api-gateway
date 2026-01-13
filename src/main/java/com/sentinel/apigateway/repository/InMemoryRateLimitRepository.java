package com.sentinel.apigateway.repository;

import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class InMemoryRateLimitRepository implements  RateLimitRepository {
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public long recordRequest(String apiKey, long windowMs, int maxRequests) {
        //temporary Fixed window logic. dont forget to change later.
        AtomicInteger counter = counters.computeIfAbsent(apiKey, k -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    @Override
    public long getCurrentCount(String apiKey, long windowMs) {
        AtomicInteger counter = counters.get(apiKey);
        return (counter != null) ? counter.get() : 0;
    }
}
