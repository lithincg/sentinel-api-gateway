package com.sentinel.apigateway.service;

import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private int requestCount = 0;

    public boolean allowRequest() {
        synchronized(this) {
            if (requestCount < 100) {
                requestCount++;
                return true;
            }
        }
            return false;

    }

    public synchronized int getRequestCount() {
        return requestCount;
    }
}