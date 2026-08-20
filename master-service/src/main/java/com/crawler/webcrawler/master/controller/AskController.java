package com.crawler.webcrawler.master.controller;

import com.crawler.webcrawler.master.service.RAGService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private final RAGService ragService;

    public AskController(RAGService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/ask")
    public RAGService.AskResult ask(@RequestParam String q,
                                    @RequestParam(defaultValue = "5") int topK) {
        return ragService.ask(q, topK);
    }
}