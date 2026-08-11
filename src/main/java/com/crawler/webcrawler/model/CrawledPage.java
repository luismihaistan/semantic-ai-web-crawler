package com.crawler.webcrawler.model;

import java.time.Instant;
import java.util.Set;

public record CrawledPage(
        String url,
        String title,
        String text,
        Set<String> links,
        Instant crawledAt
) {}