package com.sentinel.apigateway.service;

import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private int requestCount = 0;

    public boolean allowRequest() {
        if (requestCount < 100) {
            requestCount++;
            return true;
        }
        return false;
    }

    public int getRequestCount() {
        return requestCount;
    }
}