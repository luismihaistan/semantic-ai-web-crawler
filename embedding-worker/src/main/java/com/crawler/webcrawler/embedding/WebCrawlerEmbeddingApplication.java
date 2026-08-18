package com.crawler.webcrawler.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crawler.webcrawler.embedding", "com.crawler.webcrawler.common"})
public class WebCrawlerEmbeddingApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebCrawlerEmbeddingApplication.class, args);
    }
}