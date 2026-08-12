package com.crawler.webcrawler.common.service;

import com.crawler.webcrawler.common.model.CrawledPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

@Service
public class PageStorageService {

    private final Path outputDir;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public PageStorageService(@Value("${crawler.storage.output-dir:./crawled-pages}") String outputDirPath) throws IOException {
        this.outputDir = Path.of(outputDirPath).toAbsolutePath();
        Files.createDirectories(outputDir);
        System.out.println("Storing crawled pages at: " + outputDir);
    }

    public void save(CrawledPage page) {
        try {
            String filename = hashUrl(page.url()) + ".json";
            Path finalPath = outputDir.resolve(filename);
            Path tempPath = outputDir.resolve(filename + ".tmp");

            objectMapper.writeValue(tempPath.toFile(), page);
            Files.move(tempPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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