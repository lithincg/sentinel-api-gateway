package com.sentinel.apigateway.service;

import com.sentinel.apigateway.repository.RateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitRepository rateLimitRepository;

    public boolean allowRequest(String apiKey) {

        long windowMs = 60000;
        int maxRequests = 100;


        long result = rateLimitRepository.recordRequest(apiKey, windowMs, maxRequests);

        return result == 1;
    }

    public long getRequestCount(String apiKey) {
        return rateLimitRepository.getCurrentCount(apiKey, 60000);
    }
}