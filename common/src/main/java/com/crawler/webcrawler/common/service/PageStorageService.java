package com.crawler.webcrawler.common.service;

import com.crawler.webcrawler.common.model.CrawledPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

@Service
public class PageStorageService {

    private static final Path OUTPUT_DIR = Path.of("crawled-pages");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public PageStorageService() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
    }

    public void save(CrawledPage page) {
        try {
            String filename = hashUrl(page.url()) + ".json";
            Path filePath = OUTPUT_DIR.resolve(filename);
            objectMapper.writeValue(filePath.toFile(), page);
            System.out.println("Saved page to: " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to save page: " + page.url() + " -> " + e.getMessage());
        }
    }

    private String hashUrl(String url) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new IOException("Hashing failed", e);
        }
    }
}