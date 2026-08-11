package com.crawler.webcrawler.master;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.crawler.webcrawler.master", "com.crawler.webcrawler.common"})
public class WebCrawlerMasterApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebCrawlerMasterApplication.class, args);
    }
}