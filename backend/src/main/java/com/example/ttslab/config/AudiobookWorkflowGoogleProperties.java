package com.example.ttslab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audiobook-workflow.google")
public record AudiobookWorkflowGoogleProperties(String serviceAccountJsonB64) {
    public AudiobookWorkflowGoogleProperties {
        serviceAccountJsonB64 = serviceAccountJsonB64 == null ? "" : serviceAccountJsonB64.trim();
    }
}

