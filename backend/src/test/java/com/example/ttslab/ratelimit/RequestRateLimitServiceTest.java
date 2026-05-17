package com.example.ttslab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.chat.ChatUsageWindowCalculator;
import com.example.ttslab.prompts.ModelType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RequestRateLimitServiceTest {
    @Test
    void defaultTextModelLimitIsThreeTimesSpeechModelLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            3,
            RequestRateLimitUnit.WORDS
        );
        RequestRateLimitRepository repository = Mockito.mock(RequestRateLimitRepository.class);
        CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
        when(repository.findOverrideLimit("user-1", ModelType.TEXT_MODEL)).thenReturn(Optional.empty());
        when(repository.findOverrideLimit("user-1", ModelType.SPEECH_MODEL)).thenReturn(Optional.empty());
        when(repository.usedAmount("user-1", ModelType.TEXT_MODEL, 41160L)).thenReturn(0L);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(0L);

        RequestRateLimitService service = new RequestRateLimitService(
            properties,
            repository,
            new ChatUsageWindowCalculator(),
            Clock.fixed(Instant.parse("2026-05-07T12:00:00Z"), ZoneOffset.UTC)
        );

        RequestRateLimitSummaryResponse summary = service.summary(user);

        assertThat(limit(summary, ModelType.TEXT_MODEL).limit()).isEqualTo(1800L);
        assertThat(limit(summary, ModelType.SPEECH_MODEL).limit()).isEqualTo(600L);
    }

    private RequestRateLimitSummaryItem limit(RequestRateLimitSummaryResponse summary, ModelType modelType) {
        return summary.limits().stream()
            .filter(item -> item.modelType() == modelType)
            .findFirst()
            .orElseThrow();
    }
}
