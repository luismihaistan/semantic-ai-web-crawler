package com.crawler.webcrawler.service;

import com.crawler.webcrawler.model.CrawledPage;
import com.crawler.webcrawler.model.Job;
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
    private final RateLimiterService rateLimiterService;

    public CrawlOrchestratorService(RedisStreamQueueService redisStreamQueueService,
                                    CrawlerService crawlerService,
                                    PageStorageService pageStorageService,
                                    RobotsTxtService robotsTxtService,
                                    RateLimiterService rateLimiterService) {
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
            long delay = rateLimiterService.resolveDelay(rules);
            rateLimiterService.waitIfNeeded(domain, delay);

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

    private boolean isSameDomain(String url, String seedDomain) {
        try {
            return seedDomain.equals(URI.create(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }
}