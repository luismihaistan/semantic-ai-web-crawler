package com.crawler.webcrawler.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crawler.webcrawler.analyzer", "com.crawler.webcrawler.common"})
public class WebCrawlerAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebCrawlerAnalyzerApplication.class, args);
    }
}