package com.example.ttslab.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "request-limits")
public record RequestRateLimitProperties(
    boolean enabled,
    Duration window,
    long speechModelLimit,
    long textModelMultiplier,
    RequestRateLimitUnit unit
) {
    public RequestRateLimitProperties {
        window = window == null ? Duration.ofHours(12) : window;
        speechModelLimit = speechModelLimit <= 0 ? 600 : speechModelLimit;
        textModelMultiplier = textModelMultiplier <= 0 ? 1 : textModelMultiplier;
        unit = unit == null ? RequestRateLimitUnit.WORDS : unit;
    }
}
