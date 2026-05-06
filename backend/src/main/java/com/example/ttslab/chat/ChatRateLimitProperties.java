package com.example.ttslab.chat;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat-limit")
public record ChatRateLimitProperties(
    boolean enabled,
    Duration window,
    int maxRequests,
    String idHeader
) {
    public ChatRateLimitProperties {
        window = window == null ? Duration.ofHours(1) : window;
        maxRequests = maxRequests <= 0 ? 100 : maxRequests;
        idHeader = idHeader == null || idHeader.isBlank() ? "X-User-Id" : idHeader;
    }
}
