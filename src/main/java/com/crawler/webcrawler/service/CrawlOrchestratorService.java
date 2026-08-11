package com.crawler.webcrawler.service;

import com.crawler.webcrawler.model.CrawledPage;
import com.crawler.webcrawler.model.Job;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

@Service
public class CrawlOrchestratorService {

    private final RedisQueueService redisQueueService;
    private final CrawlerService crawlerService;
    private final PageStorageService pageStorageService;
    private final RobotsTxtService robotsTxtService;
    private final RateLimiterService rateLimiterService;

    public CrawlOrchestratorService(RedisQueueService redisQueueService,
                                    CrawlerService crawlerService,
                                    PageStorageService pageStorageService,
                                    RobotsTxtService robotsTxtService,
                                    RateLimiterService rateLimiterService) {
        this.redisQueueService = redisQueueService;
        this.crawlerService = crawlerService;
        this.pageStorageService = pageStorageService;
        this.robotsTxtService = robotsTxtService;
        this.rateLimiterService = rateLimiterService;
    }

    public void crawl(Job job) {
        String jobId = job.getId();
        String seedUrl = job.getUrl();
        String seedDomain = URI.create(seedUrl).getHost();

        redisQueueService.pushIfNew(jobId, seedUrl);
        redisQueueService.setJobTtl(jobId, Duration.ofHours(24));

        String url;
        while (job.getPagesCrawled() < job.getMaxPages() && (url = redisQueueService.popUrl(jobId)) != null) {

            var rules = robotsTxtService.getRules(url);
            if (!rules.isAllowed(url)) {
                System.out.println("Blocked by robots.txt: " + url);
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
                    redisQueueService.pushIfNew(jobId, link);
                }
            }
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