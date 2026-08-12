package com.crawler.webcrawler.common.service;

import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RedisStreamQueueService {

    private static final String CONSUMER_GROUP = "crawler-group";

    private final StringRedisTemplate redisTemplate;

    public RedisStreamQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String streamKey(String jobId) {
        return "URL_TO_CRAWL:" + jobId;
    }

    private String visitedKey(String jobId) {
        return "VISITED_URLS:" + jobId;
    }

    // Creates the consumer group if it doesn't exist yet.
    // Must be called before the first read on a given stream.
    public void ensureConsumerGroup(String jobId) {
        String key = streamKey(jobId);
        try {
            redisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception e) {
            // Group already exists - this is expected on subsequent calls, not an error
            if (!e.getMessage().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    // Adds a URL if it hasn't been seen before in this job. Returns true if it was new.
    public boolean pushIfNew(String jobId, String url) {
        Boolean isNew = redisTemplate.opsForSet().add(visitedKey(jobId), url) > 0;
        if (Boolean.TRUE.equals(isNew)) {
            redisTemplate.opsForStream().add(streamKey(jobId), Map.of("url", url));
        }
        return Boolean.TRUE.equals(isNew);
    }

    // Reads one message from the stream as the given consumer.
    // Returns null if nothing is available.
    public MapRecord<String, Object, Object> poll(String jobId, String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, consumerName),
                org.springframework.data.redis.connection.stream.StreamReadOptions.empty()
                        .count(1)
                        .block(Duration.ofSeconds(2)),
                StreamOffset.create(streamKey(jobId), ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.get(0);
    }

    // Confirms a message was processed successfully - removes it from the pending list
    public void ack(String jobId, String recordId) {
        redisTemplate.opsForStream().acknowledge(streamKey(jobId), CONSUMER_GROUP, recordId);
    }

    public void setJobTtl(String jobId, Duration ttl) {
        redisTemplate.expire(visitedKey(jobId), ttl);
        redisTemplate.expire(streamKey(jobId), ttl);
    }

    public long getStreamLength(String jobId) {
        Long length = redisTemplate.opsForStream().size(streamKey(jobId));
        return length != null ? length : 0;
    }

    public long getPendingCount(String jobId) {
        var summary = redisTemplate.opsForStream().pending(streamKey(jobId), CONSUMER_GROUP);
        return summary != null ? summary.getTotalPendingMessages() : 0;
    }
}