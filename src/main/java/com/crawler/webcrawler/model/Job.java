package com.crawler.webcrawler.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class Job {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    private final String id;
    private final String url;
    private final int maxPages;
    private volatile Status status;
    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private volatile String errorMessage;
    private final Instant createdAt;
    private volatile Instant completedAt;

    public Job(String id, String url, int maxPages) {
        this.id = id;
        this.url = url;
        this.maxPages = maxPages;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public int getMaxPages() { return maxPages; }
    public Status getStatus() { return status; }
    public int getPagesCrawled() { return pagesCrawled.get(); }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(Status status) { this.status = status; }
    public void incrementPagesCrawled() { pagesCrawled.incrementAndGet(); }
    public void setError(String message) { this.errorMessage = message; }
    public void markCompleted() { this.completedAt = Instant.now(); }
}