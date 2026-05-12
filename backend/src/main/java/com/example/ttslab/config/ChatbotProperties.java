package com.example.ttslab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chatbot")
public class ChatbotProperties {
    private final String provider;

    public ChatbotProperties(String provider) {
        this.provider = normalize(provider);
    }

    public String provider() {
        return provider;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "mock";
        }
        return value.trim().toLowerCase();
    }
}
