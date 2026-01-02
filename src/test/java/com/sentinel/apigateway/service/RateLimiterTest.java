package com.sentinel.apigateway.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RateLimiterService service = new RateLimiterService();
        int threadCount = 50;
        int totalRequests = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (service.allowRequest()) {
                        successCounter.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Allowed: " + successCounter.get());
        System.out.println("Actual Count: " + service.getRequestCount());

        assertTrue(completed, "Test timed out");
        assertTrue(service.getRequestCount() > 100, "Race condition failed to trigger");
    }
}