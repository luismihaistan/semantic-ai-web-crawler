package com.crawler.webcrawler.embedding.service;

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
public class HuggingFaceEmbeddingClient {

    private static final String MODEL = "sentence-transformers/all-MiniLM-L6-v2";
    private static final int MAX_INPUT_LENGTH = 2000; // keep payloads reasonable

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${huggingface.api.token}")
    private String apiToken;

    @PostConstruct
    public void validateToken() {
        if (apiToken == null || apiToken.isBlank() || !apiToken.startsWith("hf_")) {
            throw new IllegalStateException("HUGGINGFACE_API_TOKEN is missing or invalid. Check your .env file.");
        }
    }

    public float[] embed(String text) {
        try {
            String truncated = text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text;
            String requestBody = objectMapper.writeValueAsString(Map.of("inputs", truncated));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://router.huggingface.co/hf-inference/models/" + MODEL + "/pipeline/feature-extraction"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("HuggingFace API returned " + response.statusCode() + ": " + response.body());
            }

            return parseEmbedding(response.body());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    // The API returns either a flat vector (sentence-level, the common case for
    // sentence-transformers models) or a nested array of per-token vectors,
    // depending on how the model is tagged. This handles both cases defensively,
    // mean-pooling token vectors if needed.
    private float[] parseEmbedding(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.isArray() && root.size() > 0 && root.get(0).isArray()) {
            int dims = root.get(0).size();
            float[] vector = new float[dims];
            for (JsonNode tokenVector : root) {
                for (int i = 0; i < dims; i++) {
                    vector[i] += (float) tokenVector.get(i).asDouble();
                }
            }
            for (int i = 0; i < dims; i++) {
                vector[i] /= root.size();
            }
            return vector;
        }

        float[] vector = new float[root.size()];
        for (int i = 0; i < root.size(); i++) {
            vector[i] = (float) root.get(i).asDouble();
        }
        return vector;
    }
}
