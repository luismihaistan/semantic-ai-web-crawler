package com.crawler.webcrawler.common.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Service
public class CrawlerService {

    private static final int TIMEOUT_MS = 5000;

    // Elements that are almost never part of the actual content, regardless of site
    private static final String[] NOISE_SELECTORS = {
            "nav", "header", "footer", "aside", "script", "style", "noscript",
            "form", "button", "iframe",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]",
            ".navbox", ".navigation", ".menu", ".sidebar", ".footer", ".header",
            ".cookie-banner", ".ad", ".advertisement", ".breadcrumb",
            "#mw-navigation", "#footer", "#mw-head", "#siteSub", ".vector-header",
            ".vector-page-toolbar", ".vector-column-start", ".vector-column-end",
            ".mw-editsection", ".navbox-styles", ".catlinks"
    };

    public record CrawlResult(String title, String text, Set<String> links) {}

    public CrawlResult crawlAndExtract(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get();

            String title = document.title();

            // Extract links BEFORE stripping noise elements, since nav/footer links
            // are still valid for crawling purposes - we only want cleaner TEXT
            Set<String> links = new HashSet<>();
            for (Element link : document.select("a[href]")) {
                String absUrl = link.absUrl("href");
                if (absUrl.startsWith("http")) {
                    links.add(absUrl);
                }
            }

            String mainText = extractMainContent(document);

            System.out.println("Crawled: " + title + " | " + links.size() + " links | " + mainText.length() + " chars of main content");
            return new CrawlResult(title, mainText, links);

        } catch (IOException e) {
            System.err.println("Failed: " + url + " -> " + e.getMessage());
            return new CrawlResult(null, null, Set.of());
        }
    }

    private String extractMainContent(Document document) {
        Document clone = document.clone();

        for (String selector : NOISE_SELECTORS) {
            clone.select(selector).remove();
        }

        Element bestCandidate = findDensestTextBlock(clone);
        return bestCandidate != null ? bestCandidate.text() : clone.body().text();
    }

    // Finds the element with the best text-to-link ratio among reasonably sized candidates.
    // A real content block has long sentences and few links; a menu/nav block has many
    // short, link-heavy fragments even after the obvious noise selectors are removed.
    private Element findDensestTextBlock(Document document) {
        Elements candidates = document.select("article, main, #content, .content, #mw-content-text, .mw-parser-output, div, section");

        Element best = null;
        double bestScore = 0;

        for (Element candidate : candidates) {
            String text = candidate.ownText().isBlank() ? candidate.text() : candidate.text();
            int textLength = text.length();

            if (textLength < 200) {
                continue; // too small to be the main content block
            }

            int linkTextLength = 0;
            for (Element link : candidate.select("a")) {
                linkTextLength += link.text().length();
            }

            double linkDensity = textLength > 0 ? (double) linkTextLength / textLength : 1.0;
            double score = textLength * (1.0 - linkDensity);

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }
}