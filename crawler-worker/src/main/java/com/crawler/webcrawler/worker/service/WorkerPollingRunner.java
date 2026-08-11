package com.crawler.webcrawler.worker.service;

import com.crawler.webcrawler.common.service.JobStatusService;
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

    public WorkerPollingRunner(JobStatusService jobStatusService,
                               CrawlOrchestratorService crawlOrchestratorService) {
        this.jobStatusService = jobStatusService;
        this.crawlOrchestratorService = crawlOrchestratorService;
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
                    // Nothing available right now for this job - it might just be waiting
                    // on rate limiting elsewhere, or it might genuinely be done.
                    // We only mark it complete once the queue is confirmed empty AND
                    // we've reached this point with nothing to do.
                    jobStatusService.markCompleted(jobId);
                    System.out.println("Job completed (queue empty): " + jobId);
                }

            } catch (Exception e) {
                System.err.println("Error processing job " + jobId + ": " + e.getMessage());
                jobStatusService.markFailed(jobId, e.getMessage());
            }
        }
    }
}