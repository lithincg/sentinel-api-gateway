package com.sentinel.apigateway.service;

import com.sentinel.apigateway.repository.InMemoryRateLimitRepository;
import com.sentinel.apigateway.repository.RateLimitRepository;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RateLimitRepository repository = new InMemoryRateLimitRepository();
        RateLimiterService service = new RateLimiterService(repository);
        int totalRequests = 1000;
        CountDownLatch latch = new CountDownLatch(1);
        Thread[] threads = new Thread[totalRequests];

        for (int i = 0; i < totalRequests; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                    service.allowRequest("USER_X");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        latch.countDown();

        for (Thread t : threads) {
            t.join();
        }
        System.out.println("Actual Count: " + service.getRequestCount("USER_X"));
        assertTrue(service.getRequestCount("USER_X") > 0);
        assertTrue(service.getRequestCount("USER_X") <= totalRequests);
    }
    @Test
    void testPerUserIsolation() {
        RateLimitRepository repository = new InMemoryRateLimitRepository();
        RateLimiterService service = new RateLimiterService(repository);
        String userA = "ALPHA";
        String userB = "BETA";
        for (int i = 0; i < 100; i++) {
            service.allowRequest(userA);
        }

        boolean allowedA = service.allowRequest(userA);
        org.junit.jupiter.api.Assertions.assertFalse(allowedA, "User A should be blocked");

        boolean allowedB = service.allowRequest(userB);
        org.junit.jupiter.api.Assertions.assertTrue(allowedB, "User B should be allowed even if A is blocked");

        org.junit.jupiter.api.Assertions.assertEquals(1, service.getRequestCount(userB));
    }
}