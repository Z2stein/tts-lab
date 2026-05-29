package com.example.ttslab.audiobooks.workflow.concurrency;

import java.time.Duration;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "speech-model-concurrency")
@Validated
public record SpeechModelConcurrencyProperties(
    boolean enabled,
    @Positive
    int maxConcurrent,
    @NotNull
    @DurationMin(seconds = 1)
    Duration acquireTimeout
) {
}
