package com.crawler.webcrawler.service;

import com.crawler.webcrawler.model.CrawledPage;
import com.crawler.webcrawler.model.Job;
import crawlercommons.robots.BaseRobotRules;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Service
public class CrawlOrchestratorService {

    private final RedisStreamQueueService redisStreamQueueService;
    private final CrawlerService crawlerService;
    private final PageStorageService pageStorageService;
    private final RobotsTxtService robotsTxtService;
    private final DistributedRateLimiterService rateLimiterService;
    private static final long DEFAULT_DELAY_MS = 1000;

    public CrawlOrchestratorService(RedisStreamQueueService redisStreamQueueService,
                                    CrawlerService crawlerService,
                                    PageStorageService pageStorageService,
                                    RobotsTxtService robotsTxtService,
                                    DistributedRateLimiterService rateLimiterService) {
        this.redisStreamQueueService = redisStreamQueueService;
        this.crawlerService = crawlerService;
        this.pageStorageService = pageStorageService;
        this.robotsTxtService = robotsTxtService;
        this.rateLimiterService = rateLimiterService;
    }

    public void crawl(Job job) {
        String jobId = job.getId();
        String seedUrl = job.getUrl();
        String seedDomain = URI.create(seedUrl).getHost();
        String consumerName = "worker-" + Thread.currentThread().getId();

        redisStreamQueueService.ensureConsumerGroup(jobId);
        redisStreamQueueService.pushIfNew(jobId, seedUrl);
        redisStreamQueueService.setJobTtl(jobId, Duration.ofHours(24));

        while (job.getPagesCrawled() < job.getMaxPages()) {
            MapRecord<String, Object, Object> record = redisStreamQueueService.poll(jobId, consumerName);
            if (record == null) {
                // Nothing left in the queue right now - the job is effectively done
                break;
            }

            String url = (String) record.getValue().get("url");

            var rules = robotsTxtService.getRules(url);
            if (!rules.isAllowed(url)) {
                System.out.println("Blocked by robots.txt: " + url);
                redisStreamQueueService.ack(jobId, record.getId().getValue());
                continue;
            }

            String domain = URI.create(url).getHost();
            long delay = resolveDelay(rules);
            rateLimiterService.acquire(domain, delay);

            var result = crawlerService.crawlAndExtract(url);
            job.incrementPagesCrawled();

            if (result.title() != null) {
                pageStorageService.save(new CrawledPage(
                        url, result.title(), result.text(), result.links(), Instant.now()
                ));
            }

            for (String link : result.links()) {
                if (isSameDomain(link, seedDomain)) {
                    redisStreamQueueService.pushIfNew(jobId, link);
                }
            }

            // Only ack after successful processing - if we crashed above, this message
            // stays pending and can be reclaimed later instead of being lost
            redisStreamQueueService.ack(jobId, record.getId().getValue());
        }

        System.out.println("--- DONE: job " + jobId + " crawled " + job.getPagesCrawled() + " pages ---");
    }

    public long resolveDelay(BaseRobotRules rules) {
        long crawlDelaySeconds = rules.getCrawlDelay();
        // crawler-commons returns -1 (UNSET_CRAWL_DELAY) if not specified
        if (crawlDelaySeconds > 0) {
            return crawlDelaySeconds * 1000;
        }
        return DEFAULT_DELAY_MS;
    }

    private boolean isSameDomain(String url, String seedDomain) {
        try {
            return seedDomain.equals(URI.create(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }
}