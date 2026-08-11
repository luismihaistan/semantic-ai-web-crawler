package com.crawler.webcrawler.controller;

import com.crawler.webcrawler.model.Job;
import com.crawler.webcrawler.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CrawlController {

    private final JobService jobService;

    public CrawlController(JobService jobService) {
        this.jobService = jobService;
    }

    public record CrawlRequest(String url, Integer maxPages) {}

    public record JobResponse(
            String jobId,
            String url,
            String status,
            int pagesCrawled,
            String errorMessage
    ) {
        static JobResponse from(Job job) {
            return new JobResponse(
                    job.getId(),
                    job.getUrl(),
                    job.getStatus().name(),
                    job.getPagesCrawled(),
                    job.getErrorMessage()
            );
        }
    }

    @PostMapping("/crawl")
    public JobResponse startCrawl(@RequestBody CrawlRequest request) {
        int maxPages = request.maxPages() != null ? request.maxPages() : 20;
        Job job = jobService.createJob(request.url(), maxPages);
        return JobResponse.from(job);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String id) {
        Job job = jobService.getJob(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(JobResponse.from(job));
    }
}