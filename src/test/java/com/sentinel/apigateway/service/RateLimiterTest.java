package com.sentinel.apigateway.service;

import com.sentinel.apigateway.repository.InMemoryRateLimitRepository;
import com.sentinel.apigateway.repository.RateLimitRepository;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RateLimitRepository repository = new InMemoryRateLimitRepository();
        RateLimiterService service = new RateLimiterService(repository);
        int totalRequests = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        Thread[] threads = new Thread[totalRequests];

        for (int i = 0; i < totalRequests; i++) {
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    if (service.allowRequest("USER_X")) {
                        allowed.incrementAndGet();
                    } else {
                        blocked.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        startLatch.countDown();

        for (Thread t : threads) {
            t.join();
        }
        assertEquals(100, allowed.get(), "Exactly 100 requests should be allowed per window");
        assertEquals(900, blocked.get(), "Remaining 900 concurrent requests should be rate-limited");
        assertEquals(100, service.getRequestCount("USER_X"));
    }
    @Test
    void testPerUserIsolation() {
        RateLimitRepository repository = new InMemoryRateLimitRepository();
        RateLimiterService service = new RateLimiterService(repository);
        String userA = "ALPHA";
        String userB = "BETA";
        for (int i = 0; i < 100; i++) {
            assertTrue(service.allowRequest(userA));
        }

        boolean allowedA = service.allowRequest(userA);
        assertFalse(allowedA, "User A should be blocked");

        boolean allowedB = service.allowRequest(userB);
        assertTrue(allowedB, "User B should be allowed even if A is blocked");

        assertEquals(1, service.getRequestCount(userB));
    }
}