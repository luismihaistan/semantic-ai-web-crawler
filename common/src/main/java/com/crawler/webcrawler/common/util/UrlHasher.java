package com.crawler.webcrawler.common.util;

import java.security.MessageDigest;

public class UrlHasher {

    public static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}