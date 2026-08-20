package com.crawler.webcrawler.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class HuggingFaceSummarizationClient {

    private static final String MODEL = "facebook/bart-large-cnn";
    private static final int MAX_INPUT_LENGTH = 3000; // model has its own context limits

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${huggingface.api.token}")
    private String apiToken;

    @PostConstruct
    public void validateToken() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("HUGGINGFACE_API_TOKEN is missing. Check your .env file.");
        }
    }

    public String summarize(String text) {
        try {
            String truncated = text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text;

            if (truncated.isBlank() || truncated.length() < 100) {
                return null; // not enough content to meaningfully summarize
            }

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "inputs", truncated,
                    "parameters", Map.of("max_length", 130, "min_length", 30)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://router.huggingface.co/hf-inference/models/" + MODEL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HuggingFace summarization API returned " + response.statusCode() + ": " + response.body());
            }

            return parseSummary(response.body());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate summary: " + e.getMessage(), e);
        }
    }

    private String parseSummary(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.isArray() && root.size() > 0) {
            return root.get(0).path("summary_text").asText();
        }
        throw new RuntimeException("Unexpected summarization response format: " + responseBody);
    }
}