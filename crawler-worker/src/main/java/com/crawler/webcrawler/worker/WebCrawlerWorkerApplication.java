package com.crawler.webcrawler.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crawler.webcrawler.worker", "com.crawler.webcrawler.common"})
public class WebCrawlerWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebCrawlerWorkerApplication.class, args);
    }
}