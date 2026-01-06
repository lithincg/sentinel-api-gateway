package com.sentinel.apigateway.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public boolean allowRequest(String apiKey) {
        AtomicInteger counter = counters.computeIfAbsent(apiKey, k -> new AtomicInteger(0));

        int current = counter.incrementAndGet();
        if (current <= 100) {
            return true;
        }
        counter.decrementAndGet();
        return false;
    }

    public int getRequestCount(String apiKey) {
        return counters.computeIfAbsent(apiKey, k -> new AtomicInteger(0)).get();
    }
}