package com.crawler.webcrawler.common.service;

import com.crawler.webcrawler.common.util.UrlHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElasticsearchIndexService {

    private static final String INDEX_NAME = "crawled_pages";
    private static final int EMBEDDING_DIMS = 384;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${elasticsearch.host:http://localhost:9200}")
    private String esHost;

    @PostConstruct
    public void ensureIndexExists() {
        try {
            HttpRequest checkRequest = HttpRequest.newBuilder()
                    .uri(URI.create(esHost + "/" + INDEX_NAME))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> checkResponse = httpClient.send(checkRequest, HttpResponse.BodyHandlers.discarding());

            if (checkResponse.statusCode() == 404) {
                String mapping = """
                        {
                          "mappings": {
                            "properties": {
                              "url": { "type": "keyword" },
                              "title": { "type": "text" },
                              "text": { "type": "text" },
                              "embedding": {
                                "type": "dense_vector",
                                "dims": %d,
                                "index": true,
                                "similarity": "cosine"
                              },
                              "indexedAt": { "type": "date" }
                            }
                          }
                        }
                        """.formatted(EMBEDDING_DIMS);

                HttpRequest createRequest = HttpRequest.newBuilder()
                        .uri(URI.create(esHost + "/" + INDEX_NAME))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(mapping))
                        .build();

                HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
                System.out.println("Created Elasticsearch index: " + createResponse.body());
            } else {
                System.out.println("Elasticsearch index already exists");
            }
        } catch (Exception e) {
            System.err.println("Failed to ensure Elasticsearch index exists: " + e.getMessage());
        }
    }

    public void indexDocument(String url, String title, String text, float[] embedding) {
        try {
            String docId = UrlHasher.hash(url);

            Map<String, Object> doc = new HashMap<>();
            doc.put("url", url);
            doc.put("title", title);
            doc.put("text", text);
            doc.put("embedding", embedding);
            doc.put("indexedAt", Instant.now().toString());

            String body = objectMapper.writeValueAsString(doc);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(esHost + "/" + INDEX_NAME + "/_doc/" + docId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Elasticsearch indexing failed: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to index document for " + url + ": " + e.getMessage());
        }
    }

    public List<Map<String, Object>> searchSimilar(float[] queryEmbedding, int topK) {
        try {
            Map<String, Object> knnQuery = Map.of(
                    "field", "embedding",
                    "query_vector", queryEmbedding,
                    "k", topK,
                    "num_candidates", topK * 10
            );

            Map<String, Object> requestBody = Map.of(
                    "knn", knnQuery,
                    "_source", List.of("url", "title", "text")
            );

            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(esHost + "/" + INDEX_NAME + "/_search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Elasticsearch search failed: " + response.body());
            }

            return parseSearchResults(response.body());

        } catch (Exception e) {
            throw new RuntimeException("Failed to search Elasticsearch: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> parseSearchResults(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode hits = root.path("hits").path("hits");

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode hit : hits) {
            Map<String, Object> result = new HashMap<>();
            result.put("score", hit.path("_score").asDouble());
            result.put("url", hit.path("_source").path("url").asText());
            result.put("title", hit.path("_source").path("title").asText());

            String text = hit.path("_source").path("text").asText();
            String snippet = text.length() > 300 ? text.substring(0, 300) + "..." : text;
            result.put("snippet", snippet);

            results.add(result);
        }
        return results;
    }

    public void updateSummary(String url, String summary) {
        try {
            String docId = UrlHasher.hash(url);

            Map<String, Object> update = Map.of("doc", Map.of("summary", summary));
            String body = objectMapper.writeValueAsString(update);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(esHost + "/" + INDEX_NAME + "/_update/" + docId))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Elasticsearch update failed: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to update summary for " + url + ": " + e.getMessage());
        }
    }
}