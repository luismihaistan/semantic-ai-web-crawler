package com.crawler.webcrawler.service;

import com.crawler.webcrawler.model.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class JobService {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final CrawlOrchestratorService crawlOrchestratorService;

    public JobService(CrawlOrchestratorService crawlOrchestratorService) {
        this.crawlOrchestratorService = crawlOrchestratorService;
    }

    public Job createJob(String url, int maxPages) {
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, url, maxPages);
        jobs.put(jobId, job);

        executor.submit(() -> runJob(job));

        return job;
    }

    private void runJob(Job job) {
        job.setStatus(Job.Status.RUNNING);
        try {
            crawlOrchestratorService.crawl(job);
            job.setStatus(Job.Status.COMPLETED);
        } catch (Exception e) {
            job.setError(e.getMessage());
            job.setStatus(Job.Status.FAILED);
        } finally {
            job.markCompleted();
        }
    }

    public Job getJob(String jobId) {
        return jobs.get(jobId);
    }
}