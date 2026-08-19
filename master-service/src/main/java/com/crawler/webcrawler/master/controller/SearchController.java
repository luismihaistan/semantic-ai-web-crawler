package com.crawler.webcrawler.master.controller;

import com.crawler.webcrawler.common.service.ElasticsearchIndexService;
import com.crawler.webcrawler.common.service.HuggingFaceEmbeddingClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final HuggingFaceEmbeddingClient embeddingClient;
    private final ElasticsearchIndexService indexService;

    public SearchController(HuggingFaceEmbeddingClient embeddingClient, ElasticsearchIndexService indexService) {
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q,
                                            @RequestParam(defaultValue = "5") int topK) {
        float[] queryEmbedding = embeddingClient.embed(q);
        return indexService.searchSimilar(queryEmbedding, topK);
    }
}