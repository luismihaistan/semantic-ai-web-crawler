package com.crawler.webcrawler.analyzer.service;

import com.crawler.webcrawler.common.service.HuggingFaceSummarizationClient;
import com.crawler.webcrawler.common.service.JobStatusService;
import com.crawler.webcrawler.common.service.RedisStreamQueueService;
import com.crawler.webcrawler.common.service.ElasticsearchIndexService;
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
public class AnalyzerWorkerPollingRunner {

    private static final String CONSUMER_NAME = "analyzer-worker-" + UUID.randomUUID();
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(10);

    private final JobStatusService jobStatusService;
    private final RedisStreamQueueService redisStreamQueueService;
    private final HuggingFaceSummarizationClient summarizationClient;
    private final ElasticsearchIndexService indexService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, Instant> lastFailureTime = new ConcurrentHashMap<>();
    private final Set<String> groupsEnsured = ConcurrentHashMap.newKeySet();

    public AnalyzerWorkerPollingRunner(JobStatusService jobStatusService,
                                       RedisStreamQueueService redisStreamQueueService,
                                       HuggingFaceSummarizationClient summarizationClient,
                                       ElasticsearchIndexService indexService) {
        this.jobStatusService = jobStatusService;
        this.redisStreamQueueService = redisStreamQueueService;
        this.summarizationClient = summarizationClient;
        this.indexService = indexService;
    }

    @PostConstruct
    public void start() {
        System.out.println("Analyzer worker started with consumer name: " + CONSUMER_NAME);
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
                        jobStatusService.markAnalyzerDone(jobId);
                        lastFailureTime.remove(jobId);
                        System.out.println("Analyzer: nothing to process for job (crawl finished, no text generated): " + jobId);
                    }
                    continue;
                }

                if (groupsEnsured.add(jobId)) {
                    redisStreamQueueService.ensureAnalyzerConsumerGroup(jobId);
                }

                MapRecord<String, Object, Object> record = redisStreamQueueService.pollAnalyzer(jobId, CONSUMER_NAME);

                if (record != null) {
                    String url = (String) record.getValue().get("url");
                    String title = (String) record.getValue().get("title");
                    String text = (String) record.getValue().get("text");

                    String summary = summarizationClient.summarize(title + ". " + text);

                    if (summary != null) {
                        indexService.updateSummary(url, summary);
                        System.out.println("Summarized: " + url);
                    } else {
                        System.out.println("Skipped summarization (insufficient text): " + url);
                    }

                    redisStreamQueueService.ackAnalyzer(jobId, record.getId().getValue());
                    lastFailureTime.remove(jobId);
                } else {
                    long pendingCount = redisStreamQueueService.getAnalyzerPendingCount(jobId);
                    if (crawlFinished && pendingCount == 0) {
                        jobStatusService.markAnalyzerDone(jobId);
                        System.out.println("Analyzer processing completed for job: " + jobId);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing analyzer job " + jobId + ": " + e.getMessage());
                lastFailureTime.put(jobId, Instant.now());
            }
        }
    }
}