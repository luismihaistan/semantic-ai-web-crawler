package com.crawler.webcrawler.worker.service;

import com.crawler.webcrawler.common.model.CrawledPage;
import com.crawler.webcrawler.common.service.*;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;

@Service
public class CrawlOrchestratorService {

    private final RedisStreamQueueService redisStreamQueueService;
    private final CrawlerService crawlerService;
    private final PageStorageService pageStorageService;
    private final RobotsTxtService robotsTxtService;
    private final DistributedRateLimiterService rateLimiterService;
    private final JobStatusService jobStatusService;

    public CrawlOrchestratorService(RedisStreamQueueService redisStreamQueueService,
                                    CrawlerService crawlerService,
                                    PageStorageService pageStorageService,
                                    RobotsTxtService robotsTxtService,
                                    DistributedRateLimiterService rateLimiterService,
                                    JobStatusService jobStatusService) {
        this.redisStreamQueueService = redisStreamQueueService;
        this.crawlerService = crawlerService;
        this.pageStorageService = pageStorageService;
        this.robotsTxtService = robotsTxtService;
        this.rateLimiterService = rateLimiterService;
        this.jobStatusService = jobStatusService;
    }

    // Processes a single message for this job, if one is available.
    // Returns true if a message was found and processed (or skipped due to robots.txt),
    // false if the stream had nothing available right now.
    public boolean processOne(String jobId, String consumerName, String seedDomain) {
        MapRecord<String, Object, Object> record = redisStreamQueueService.poll(jobId, consumerName);
        if (record == null) {
            return false;
        }

        String url = (String) record.getValue().get("url");

        var rules = robotsTxtService.getRules(url);
        if (!rules.isAllowed(url)) {
            System.out.println("Blocked by robots.txt: " + url);
            redisStreamQueueService.ack(jobId, record.getId().getValue());
            return true;
        }

        String domain = URI.create(url).getHost();
        long delay = robotsTxtService.getRules(url).getCrawlDelay() > 0
                ? robotsTxtService.getRules(url).getCrawlDelay() * 1000
                : 1000;
        rateLimiterService.acquire(domain, delay);

        var result = crawlerService.crawlAndExtract(url);
        jobStatusService.incrementPagesCrawled(jobId);

        if (result.title() != null) {
            pageStorageService.save(new CrawledPage(
                    url, result.title(), result.text(), result.links(), Instant.now()
            ));
        }

        for (String link : result.links()) {
            String cleanLink = normalizeUrl(link);

            if (isSameDomain(cleanLink, seedDomain)) {
                redisStreamQueueService.pushIfNew(jobId, cleanLink);
            }
        }

        redisStreamQueueService.ack(jobId, record.getId().getValue());
        return true;
    }

    private String normalizeUrl(String url) {
        int hashIndex = url.indexOf('#');
        if (hashIndex != -1) {
            return url.substring(0, hashIndex);
        }
        return url;
    }

    private boolean isSameDomain(String url, String seedDomain) {
        try {
            return seedDomain.equals(URI.create(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }
}