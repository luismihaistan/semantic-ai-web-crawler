package com.crawler.webcrawler.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DistributedRateLimiterService {

    private static final long RETRY_INTERVAL_MS = 100;
    private static final String LOCK_VALUE = "locked";

    private final StringRedisTemplate redisTemplate;

    public DistributedRateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String rateLimitKey(String domain) {
        return "RATE_LIMIT:" + domain;
    }

    // Blocks the calling thread until it is this caller's turn to hit the given domain.
    // Safe across multiple processes and threads, since the coordination lives in Redis.
    public void acquire(String domain, long delayMs) {
        String key = rateLimitKey(domain);

        while (true) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, LOCK_VALUE, Duration.ofMillis(delayMs));

            if (Boolean.TRUE.equals(acquired)) {
                return; // we got the slot, safe to proceed with the request now
            }

            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}