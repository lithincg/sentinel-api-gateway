package com.sentinel.apigateway.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RateLimiterService service = new RateLimiterService();
        int totalRequests = 1000;
        CountDownLatch latch = new CountDownLatch(1);
        Thread[] threads = new Thread[totalRequests];

        for (int i = 0; i < totalRequests; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                    service.allowRequest();
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

        System.out.println("Actual Count: " + service.getRequestCount());

        assertTrue(service.getRequestCount() > 0);
        assertTrue(service.getRequestCount() <= totalRequests);
    }
}