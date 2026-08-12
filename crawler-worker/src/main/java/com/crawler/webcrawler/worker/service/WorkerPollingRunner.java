package com.crawler.webcrawler.worker.service;

import com.crawler.webcrawler.common.service.JobStatusService;
import com.crawler.webcrawler.common.service.RedisStreamQueueService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WorkerPollingRunner {

    private static final String CONSUMER_NAME = "worker-" + java.util.UUID.randomUUID();

    private final JobStatusService jobStatusService;
    private final CrawlOrchestratorService crawlOrchestratorService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RedisStreamQueueService redisStreamQueueService;

    public WorkerPollingRunner(JobStatusService jobStatusService,
                               CrawlOrchestratorService crawlOrchestratorService,
                               RedisStreamQueueService redisStreamQueueService) {
        this.jobStatusService = jobStatusService;
        this.crawlOrchestratorService = crawlOrchestratorService;
        this.redisStreamQueueService = redisStreamQueueService;
    }

    @PostConstruct
    public void start() {
        System.out.println("Worker started with consumer name: " + CONSUMER_NAME);
        scheduler.scheduleWithFixedDelay(this::pollActiveJobs, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void pollActiveJobs() {
        Set<String> activeJobIds = jobStatusService.getActiveJobIds();

        for (String jobId : activeJobIds) {
            try {
                jobStatusService.markRunning(jobId);

                int maxPages = jobStatusService.getMaxPages(jobId);
                int pagesCrawled = jobStatusService.getPagesCrawled(jobId);

                if (pagesCrawled >= maxPages) {
                    jobStatusService.markCompleted(jobId);
                    System.out.println("Job completed (max pages reached): " + jobId);
                    continue;
                }

                String seedUrl = jobStatusService.getUrl(jobId);
                String seedDomain = URI.create(seedUrl).getHost();

                boolean processed = crawlOrchestratorService.processOne(jobId, CONSUMER_NAME, seedDomain);

                if (!processed) {
                    // Nothing available for THIS worker right now - but that doesn't mean
                    // the job is done. Other workers might still be holding unacked
                    // messages (in flight), which could still produce new links.
                    long streamLength = redisStreamQueueService.getStreamLength(jobId);
                    long pendingCount = redisStreamQueueService.getPendingCount(jobId);

                    if (streamLength == 0 && pendingCount == 0) {
                        // Truly nothing left anywhere: no queued messages, no one mid-processing
                        jobStatusService.markCompleted(jobId);
                        System.out.println("Job completed (queue confirmed empty): " + jobId);
                    }
                    // else: some other worker is still in flight - just wait for the next poll cycle
                }

            } catch (Exception e) {
                System.err.println("Error processing job " + jobId + ": " + e.getMessage());
                jobStatusService.markFailed(jobId, e.getMessage());
            }
        }
    }
}