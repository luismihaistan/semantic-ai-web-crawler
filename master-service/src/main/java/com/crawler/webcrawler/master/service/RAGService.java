package com.crawler.webcrawler.master.service;

import com.crawler.webcrawler.common.service.ElasticsearchIndexService;
import com.crawler.webcrawler.common.service.HuggingFaceChatClient;
import com.crawler.webcrawler.common.service.HuggingFaceEmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RAGService {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant answering questions using only the provided context.
            The context comes from web pages that were crawled and indexed.
            If the context does not contain enough information to answer, say so clearly instead of guessing.
            Cite which source(s) you used by mentioning their title.
            """;

    private final HuggingFaceEmbeddingClient embeddingClient;
    private final ElasticsearchIndexService indexService;
    private final HuggingFaceChatClient chatClient;

    public RAGService(HuggingFaceEmbeddingClient embeddingClient,
                      ElasticsearchIndexService indexService,
                      HuggingFaceChatClient chatClient) {
        this.embeddingClient = embeddingClient;
        this.indexService = indexService;
        this.chatClient = chatClient;
    }

    public record AskResult(String answer, List<Map<String, Object>> sources) {}

    public AskResult ask(String question, int topK) {
        float[] questionEmbedding = embeddingClient.embed(question);

        List<Map<String, Object>> results = indexService.searchForContext(questionEmbedding, topK, 800);

        if (results.isEmpty()) {
            return new AskResult("No indexed content was found to answer this question.", List.of());
        }

        String context = buildContext(results);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question;

        String answer = chatClient.chat(SYSTEM_PROMPT, userPrompt);

        return new AskResult(answer, results);
    }

    private String buildContext(List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Map<String, Object> result : results) {
            sb.append("[Source ").append(i++).append(": ").append(result.get("title")).append("]\n");
            sb.append(result.get("text")).append("\n\n");
        }
        return sb.toString();
    }
}