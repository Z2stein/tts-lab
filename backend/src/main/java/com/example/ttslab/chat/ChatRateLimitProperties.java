package com.example.ttslab.chat;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "chat-limit")
@Validated
public record ChatRateLimitProperties(
    boolean enabled,
    @NotNull
    @DurationMin(seconds = 1)
    Duration window,
    @Positive
    int maxRequests,
    @NotBlank
    String idHeader
) {
}
