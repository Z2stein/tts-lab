package com.example.ttslab.ratelimit;

import org.springframework.stereotype.Component;

@Component
public class RequestUsageMeasurer {
    public long measure(String promptText, RequestRateLimitUnit unit) {
        if (promptText == null || promptText.isBlank()) {
            return 0;
        }
        return switch (unit) {
            case WORDS -> countWords(promptText);
            case TOKENS -> estimateTokens(promptText);
        };
    }

    private long countWords(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private long estimateTokens(String value) {
        return Math.max(1, (long) Math.ceil(value.trim().length() / 4.0));
    }
}

