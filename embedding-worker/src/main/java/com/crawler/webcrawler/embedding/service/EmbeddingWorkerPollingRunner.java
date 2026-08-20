package com.crawler.webcrawler.embedding.service;

import com.crawler.webcrawler.common.service.ElasticsearchIndexService;
import com.crawler.webcrawler.common.service.HuggingFaceEmbeddingClient;
import com.crawler.webcrawler.common.service.JobStatusService;
import com.crawler.webcrawler.common.service.RedisStreamQueueService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class EmbeddingWorkerPollingRunner {

    private static final String CONSUMER_NAME = "embedding-worker-" + UUID.randomUUID();
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(10); // Timpul de pauză după eroare

    private final JobStatusService jobStatusService;
    private final RedisStreamQueueService redisStreamQueueService;
    private final HuggingFaceEmbeddingClient embeddingClient;
    private final ElasticsearchIndexService indexService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, Instant> lastFailureTime = new ConcurrentHashMap<>();
    private final Set<String> groupsEnsured = ConcurrentHashMap.newKeySet();


    public EmbeddingWorkerPollingRunner(JobStatusService jobStatusService,
                                        RedisStreamQueueService redisStreamQueueService,
                                        HuggingFaceEmbeddingClient embeddingClient,
                                        ElasticsearchIndexService indexService) {
        this.jobStatusService = jobStatusService;
        this.redisStreamQueueService = redisStreamQueueService;
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
    }

    @PostConstruct
    public void start() {
        System.out.println("Embedding worker started with consumer name: " + CONSUMER_NAME);
        scheduler.scheduleWithFixedDelay(this::pollTextJobs, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void pollTextJobs() {
        Set<String> textJobIds = jobStatusService.getTextJobIds();

        for (String jobId : textJobIds) {
            Instant lastFailure = lastFailureTime.get(jobId);
            if (lastFailure != null && Duration.between(lastFailure, Instant.now()).compareTo(FAILURE_BACKOFF) < 0) {
                continue;
            }

            try {
                long streamLength = redisStreamQueueService.getTextStreamLength(jobId);
                boolean crawlFinished = jobStatusService.isCrawlFinished(jobId);

                if (streamLength == 0) {
                    if (crawlFinished) {
                        jobStatusService.markEmbeddingDone(jobId);
                        lastFailureTime.remove(jobId);
                        System.out.println("Embedding: nothing to process for job " + jobId);
                    }
                    continue;
                }

                if (groupsEnsured.add(jobId)) {
                    redisStreamQueueService.ensureTextConsumerGroup(jobId);
                }

                MapRecord<String, Object, Object> record = redisStreamQueueService.pollText(jobId, CONSUMER_NAME);

                if (record != null) {
                    String url = (String) record.getValue().get("url");
                    String title = (String) record.getValue().get("title");
                    String text = (String) record.getValue().get("text");

                    float[] embedding = embeddingClient.embed(title + " " + text);
                    indexService.indexDocument(url, title, text, embedding);

                    redisStreamQueueService.ackText(jobId, record.getId().getValue());
                    System.out.println("Indexed: " + url);

                    lastFailureTime.remove(jobId);
                } else {
                    long pendingCount = redisStreamQueueService.getTextPendingCount(jobId);
                    if (crawlFinished && pendingCount == 0) {
                        jobStatusService.markEmbeddingDone(jobId);
                        System.out.println("Embedding processing completed for job: " + jobId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing text job " + jobId + ": " + e.getMessage());
                lastFailureTime.put(jobId, Instant.now());
                e.printStackTrace();
            }
        }
    }
}