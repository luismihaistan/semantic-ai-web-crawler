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
import java.util.List;
import java.util.Map;

@Service
public class HuggingFaceChatClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${huggingface.api.token}")
    private String apiToken;

    // Configurable so it can be swapped without a rebuild if the provider
    // deprecates or rate-limits the default model on the free tier.
    @Value("${huggingface.chat.model:meta-llama/Llama-3.2-3B-Instruct}")
    private String model;

    @PostConstruct
    public void validateToken() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("HUGGINGFACE_API_TOKEN is missing. Check your .env file.");
        }
    }

    public String chat(String systemPrompt, String userPrompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", 400,
                    "temperature", 0.3
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://router.huggingface.co/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HuggingFace chat API returned " + response.statusCode() + ": " + response.body());
            }

            return parseAnswer(response.body());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate chat completion: " + e.getMessage(), e);
        }
    }

    private String parseAnswer(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        throw new RuntimeException("Unexpected chat completion response format: " + responseBody);
    }
}