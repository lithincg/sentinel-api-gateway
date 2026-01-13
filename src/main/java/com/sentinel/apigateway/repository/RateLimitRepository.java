package com.sentinel.apigateway.repository;

public interface RateLimitRepository {
    long recordRequest(String apiKey, long windowMs, int maxRequests);

    long getCurrentCount(String apiKey, long windowMs);
}
