package com.sentinel.apigateway.repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
public class InMemoryRateLimitRepository implements RateLimitRepository {

    private static final class WindowState {
        long windowStart;
        int count;

        WindowState(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    private final Map<String, WindowState> windows = new ConcurrentHashMap<>();

    @Override
    public long recordRequest(String apiKey, long windowMs, int maxRequests) {
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        windows.compute(apiKey, (k, state) -> {
            if (state == null || now - state.windowStart >= windowMs) {
                if (maxRequests <= 0) {
                    return new WindowState(now, 0);
                }
                allowed.set(true);
                return new WindowState(now, 1);
            }
            if (state.count < maxRequests) {
                state.count++;
                allowed.set(true);
            }
            return state;
        });

        return allowed.get() ? 1L : 0L;
    }

    @Override
    public long getCurrentCount(String apiKey, long windowMs) {
        long now = System.currentTimeMillis();
        WindowState state = windows.computeIfPresent(apiKey, (k, current) -> {
            if (now - current.windowStart >= windowMs) {
                return null;
            }
            return current;
        });
        return state != null ? state.count : 0L;
    }
}