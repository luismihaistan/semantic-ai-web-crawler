package com.crawler.webcrawler.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisQueueService {

    private final StringRedisTemplate redisTemplate;

    public RedisQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String queueKey(String jobId) {
        return "URL_TO_CRAWL:" + jobId;
    }

    private String visitedKey(String jobId) {
        return "VISITED_URLS:" + jobId;
    }

    public boolean pushIfNew(String jobId, String url) {
        Boolean isNew = redisTemplate.opsForSet().add(visitedKey(jobId), url) > 0;
        if (Boolean.TRUE.equals(isNew)) {
            redisTemplate.opsForList().rightPush(queueKey(jobId), url);
        }
        return Boolean.TRUE.equals(isNew);
    }

    public String popUrl(String jobId) {
        return redisTemplate.opsForList().leftPop(queueKey(jobId));
    }

    public void setJobTtl(String jobId, Duration ttl) {
        redisTemplate.expire(visitedKey(jobId), ttl);
        redisTemplate.expire(queueKey(jobId), ttl);
    }
}