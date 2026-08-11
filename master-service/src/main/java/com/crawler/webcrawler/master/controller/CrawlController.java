package com.crawler.webcrawler.master.controller;

import com.crawler.webcrawler.common.service.JobStatusService;
import com.crawler.webcrawler.common.service.RedisStreamQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class CrawlController {

    private final JobStatusService jobStatusService;
    private final RedisStreamQueueService redisStreamQueueService;

    public CrawlController(JobStatusService jobStatusService, RedisStreamQueueService redisStreamQueueService) {
        this.jobStatusService = jobStatusService;
        this.redisStreamQueueService = redisStreamQueueService;
    }

    public record CrawlRequest(String url, Integer maxPages) {}

    @PostMapping("/crawl")
    public Map<String, String> startCrawl(@RequestBody CrawlRequest request) {
        String jobId = UUID.randomUUID().toString();
        int maxPages = request.maxPages() != null ? request.maxPages() : 20;

        jobStatusService.createJob(jobId, request.url(), maxPages);
        redisStreamQueueService.ensureConsumerGroup(jobId);
        redisStreamQueueService.pushIfNew(jobId, request.url());
        redisStreamQueueService.setJobTtl(jobId, Duration.ofHours(24));

        return Map.of("jobId", jobId, "status", "PENDING");
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<Object, Object>> getJob(@PathVariable String id) {
        Optional<Map<Object, Object>> job = jobStatusService.getJob(id);
        return job.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}