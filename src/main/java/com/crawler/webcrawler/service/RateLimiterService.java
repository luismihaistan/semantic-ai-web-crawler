package com.crawler.webcrawler.service;

import crawlercommons.robots.BaseRobotRules;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final long DEFAULT_DELAY_MS = 1000; // fallback when robots.txt specifies nothing
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    public void waitIfNeeded(String domain, long delayMs) {
        long now = System.currentTimeMillis();
        Long lastTime = lastRequestTime.get(domain);

        if (lastTime != null) {
            long elapsed = now - lastTime;
            if (elapsed < delayMs) {
                try {
                    Thread.sleep(delayMs - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        lastRequestTime.put(domain, System.currentTimeMillis());
    }

    public long resolveDelay(BaseRobotRules rules) {
        long crawlDelaySeconds = rules.getCrawlDelay();
        // crawler-commons returns -1 (UNSET_CRAWL_DELAY) if not specified
        if (crawlDelaySeconds > 0) {
            return crawlDelaySeconds * 1000;
        }
        return DEFAULT_DELAY_MS;
    }
}