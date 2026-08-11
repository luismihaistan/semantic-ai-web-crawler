package com.crawler.webcrawler.common.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Service
public class CrawlerService {

    private static final int TIMEOUT_MS = 5000;

    public record CrawlResult(String title, String text, Set<String> links) {}

    public CrawlResult crawlAndExtract(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get();

            String title = document.title();
            String plainText = document.text();

            Set<String> links = new HashSet<>();
            for (Element link : document.select("a[href]")) {
                String absUrl = link.absUrl("href");
                if (absUrl.startsWith("http")) {
                    links.add(absUrl);
                }
            }

            System.out.println("📄 " + title + " | 🔗 " + links.size() + " linkuri găsite");
            return new CrawlResult(title, plainText, links);

        } catch (IOException e) {
            System.err.println("❌ Failed: " + url + " -> " + e.getMessage());
            return new CrawlResult(null, null, Set.of());
        }
    }
}