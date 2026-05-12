package com.example.ttslab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tts-workbench.google")
public record TtsWorkbenchGoogleProperties(String serviceAccountJsonB64) {
    public TtsWorkbenchGoogleProperties {
        serviceAccountJsonB64 = serviceAccountJsonB64 == null ? "" : serviceAccountJsonB64.trim();
    }
}
