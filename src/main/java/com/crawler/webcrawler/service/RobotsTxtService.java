package com.crawler.webcrawler.service;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class RobotsTxtService {

    private static final String USER_AGENT = "MyWebCrawler/1.0";
    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Cache: one rule set per domain, so we don't re-fetch robots.txt for every URL
    private final Map<String, BaseRobotRules> rulesCache = new HashMap<>();

    public BaseRobotRules getRules(String url) {
        String domain = URI.create(url).getHost();
        return rulesCache.computeIfAbsent(domain, this::fetchRules);
    }

    public boolean isAllowed(String url) {
        try {
            String domain = URI.create(url).getHost();
            BaseRobotRules rules = rulesCache.computeIfAbsent(domain, this::fetchRules);
            return rules.isAllowed(url);
        } catch (Exception e) {
            // If we can't determine the rules, default to the conservative choice: allow
            System.err.println("Could not check robots.txt for " + url + ": " + e.getMessage());
            return true;
        }
    }

    private BaseRobotRules fetchRules(String domain) {
        String robotsUrl = "https://" + domain + "/robots.txt";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return parser.parseContent(robotsUrl, response.body(), "text/plain", USER_AGENT);
            } else {
                // No robots.txt (404 etc.) = everything allowed, per standard convention
                return parser.parseContent(robotsUrl, new byte[0], "text/plain", USER_AGENT);
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch robots.txt for " + domain + ": " + e.getMessage());
            return parser.parseContent(robotsUrl, new byte[0], "text/plain", USER_AGENT);
        }
    }
}