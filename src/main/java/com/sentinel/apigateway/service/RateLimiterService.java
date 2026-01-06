package com.sentinel.apigateway.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final AtomicInteger requestCount = new AtomicInteger(0);

    public boolean allowRequest() {
        int current = requestCount.incrementAndGet();
        if (current <= 100) {
            return true;
        }
        requestCount.decrementAndGet();
        return false;
    }

    public int getRequestCount() {
        return requestCount.get();
    }
}