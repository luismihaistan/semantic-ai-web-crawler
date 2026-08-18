package com.crawler.webcrawler.embedding.service;

import com.crawler.webcrawler.common.util.UrlHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
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
}