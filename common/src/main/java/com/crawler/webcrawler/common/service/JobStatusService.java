package com.crawler.webcrawler.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class JobStatusService {

    private final StringRedisTemplate redisTemplate;

    public JobStatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String statusKey(String jobId) {
        return "JOB_STATUS:" + jobId;
    }

    public void createJob(String jobId, String url, int maxPages) {
        Map<String, String> fields = new HashMap<>();
        fields.put("url", url);
        fields.put("maxPages", String.valueOf(maxPages));
        fields.put("status", "PENDING");
        fields.put("pagesCrawled", "0");
        redisTemplate.opsForHash().putAll(statusKey(jobId), fields);
        redisTemplate.opsForSet().add("ACTIVE_JOBS", jobId);
        redisTemplate.opsForSet().add(TEXT_JOBS_SET, jobId);
    }

    public void markRunning(String jobId) {
        redisTemplate.opsForHash().put(statusKey(jobId), "status", "RUNNING");
    }

    public void incrementPagesCrawled(String jobId) {
        redisTemplate.opsForHash().increment(statusKey(jobId), "pagesCrawled", 1);
    }

    public void markCompleted(String jobId) {
        redisTemplate.opsForHash().put(statusKey(jobId), "status", "COMPLETED");
        redisTemplate.opsForSet().remove("ACTIVE_JOBS", jobId);
    }

    public void markFailed(String jobId, String errorMessage) {
        redisTemplate.opsForHash().put(statusKey(jobId), "status", "FAILED");
        redisTemplate.opsForHash().put(statusKey(jobId), "errorMessage", errorMessage);
        redisTemplate.opsForSet().remove("ACTIVE_JOBS", jobId);
    }

    public Optional<Map<Object, Object>> getJob(String jobId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(statusKey(jobId));
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    }

    public Set<String> getActiveJobIds() {
        return redisTemplate.opsForSet().members("ACTIVE_JOBS");
    }

    public int getMaxPages(String jobId) {
        Object value = redisTemplate.opsForHash().get(statusKey(jobId), "maxPages");
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    public int getPagesCrawled(String jobId) {
        Object value = redisTemplate.opsForHash().get(statusKey(jobId), "pagesCrawled");
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    public String getUrl(String jobId) {
        Object value = redisTemplate.opsForHash().get(statusKey(jobId), "url");
        return value != null ? value.toString() : null;
    }

    private static final String TEXT_JOBS_SET = "TEXT_JOBS";

    public void addTextJob(String jobId) {
        redisTemplate.opsForSet().add(TEXT_JOBS_SET, jobId);
    }

    public void removeTextJob(String jobId) {
        redisTemplate.opsForSet().remove(TEXT_JOBS_SET, jobId);
    }

    public Set<String> getTextJobIds() {
        return redisTemplate.opsForSet().members(TEXT_JOBS_SET);
    }

    public boolean isCrawlFinished(String jobId) {
        Object status = redisTemplate.opsForHash().get(statusKey(jobId), "status");
        return status != null && ("COMPLETED".equals(status.toString()) || "FAILED".equals(status.toString()));
    }
}