package com.crawler.webcrawler.common.service;

import org.springframework.data.redis.connection.stream.*;
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

    private static final String TEXT_CONSUMER_GROUP = "embedding-group";

    public String textStreamKey(String jobId) {
        return "TEXT_TO_ANALYZE:" + jobId;
    }

    public void pushTextForAnalysis(String jobId, String url, String title, String text) {
        redisTemplate.opsForStream().add(textStreamKey(jobId), Map.of(
                "url", url,
                "title", title != null ? title : "",
                "text", text != null ? text : ""
        ));
    }

    public void ensureTextConsumerGroup(String jobId) {
        String key = textStreamKey(jobId);
        try {
            redisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), TEXT_CONSUMER_GROUP);
        } catch (Exception e) {
            if (!isBusyGroupException(e)) {
                throw e;
            }
        }
    }

    private boolean isBusyGroupException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public MapRecord<String, Object, Object> pollText(String jobId, String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(TEXT_CONSUMER_GROUP, consumerName),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create(textStreamKey(jobId), ReadOffset.lastConsumed())
        );
        return (records == null || records.isEmpty()) ? null : records.get(0);
    }

    public void ackText(String jobId, String recordId) {
        redisTemplate.opsForStream().acknowledge(textStreamKey(jobId), TEXT_CONSUMER_GROUP, recordId);
    }

    public long getTextStreamLength(String jobId) {
        Long len = redisTemplate.opsForStream().size(textStreamKey(jobId));
        return len != null ? len : 0;
    }

    public long getTextPendingCount(String jobId) {
        var summary = redisTemplate.opsForStream().pending(textStreamKey(jobId), TEXT_CONSUMER_GROUP);
        return summary != null ? summary.getTotalPendingMessages() : 0;
    }

    private static final String ANALYZER_CONSUMER_GROUP = "analyzer-group";

    public void ensureAnalyzerConsumerGroup(String jobId) {
        String key = textStreamKey(jobId);
        try {
            redisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), ANALYZER_CONSUMER_GROUP);
        } catch (Exception e) {
            if (!isBusyGroupException(e)) throw e;
        }
    }

    public MapRecord<String, Object, Object> pollAnalyzer(String jobId, String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(ANALYZER_CONSUMER_GROUP, consumerName),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create(textStreamKey(jobId), ReadOffset.lastConsumed())
        );
        return (records == null || records.isEmpty()) ? null : records.get(0);
    }

    public void ackAnalyzer(String jobId, String recordId) {
        redisTemplate.opsForStream().acknowledge(textStreamKey(jobId), ANALYZER_CONSUMER_GROUP, recordId);
    }

    public long getAnalyzerPendingCount(String jobId) {
        var summary = redisTemplate.opsForStream().pending(textStreamKey(jobId), ANALYZER_CONSUMER_GROUP);
        return summary != null ? summary.getTotalPendingMessages() : 0;
    }
}