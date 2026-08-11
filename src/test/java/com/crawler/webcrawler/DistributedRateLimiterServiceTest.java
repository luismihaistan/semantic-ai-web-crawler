package com.crawler.webcrawler;

import com.crawler.webcrawler.service.DistributedRateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DistributedRateLimiterServiceTest {

    @Autowired
    private DistributedRateLimiterService rateLimiterService;

    @Test
    void concurrentCallersShouldBeSerializedPerDomain() throws InterruptedException {
        String domain = "test-domain-" + System.currentTimeMillis();
        long delayMs = 500;
        int callerCount = 5;

        var timestamps = new CopyOnWriteArrayList<Long>();
        var executor = Executors.newFixedThreadPool(callerCount);
        var latch = new CountDownLatch(callerCount);

        for (int i = 0; i < callerCount; i++) {
            executor.submit(() -> {
                rateLimiterService.acquire(domain, delayMs);
                timestamps.add(System.currentTimeMillis());
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        var sorted = timestamps.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            long gap = sorted.get(i) - sorted.get(i - 1);
            assertTrue(gap >= delayMs - 50,
                    "Expected at least " + delayMs + "ms between calls, got " + gap + "ms");
        }
    }
}