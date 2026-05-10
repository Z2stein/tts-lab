package com.example.ttslab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@DisplayName("RequestRateLimitService")
class RequestRateLimitServiceTest {
    private RequestRateLimitRepository repository;
    private RequestRateLimitService service;
    private Clock clock;
    private CurrentUser testUser;

    @BeforeEach
    void setup() {
        repository = Mockito.mock(RequestRateLimitRepository.class);
        clock = Clock.fixed(Instant.parse("2026-05-07T12:00:00Z"), ZoneOffset.UTC);
        testUser = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
    }

    // ============ checkAndConsume() tests ============

    @Test
    @DisplayName("checkAndConsume allows request when disabled")
    void checkAndConsumeAllowsWhenDisabled() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            false, // disabled
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(100L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 200L);

        assertThat(result.allowed()).isTrue();
        verify(repository, never()).consume(anyString(), any(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("checkAndConsume ignores zero amount")
    void checkAndConsumeIgnoresZeroAmount() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(500L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 0L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.requested()).isEqualTo(0L);
        verify(repository, never()).consume(anyString(), any(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("checkAndConsume treats negative amount as zero")
    void checkAndConsumeConvertsNegativeToZero() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(100L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, -50L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.requested()).isEqualTo(0L);
        verify(repository, never()).consume(anyString(), any(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("checkAndConsume rejects when limit exceeded")
    void checkAndConsumeRejectsWhenLimitExceeded() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(500L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 200L);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(600L);
        verify(repository, never()).consume(anyString(), any(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("checkAndConsume allows and consumes when within limit")
    void checkAndConsumeAllowsAndConsumesWhenWithinLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(300L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 200L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.requested()).isEqualTo(200L);
        verify(repository).consume(anyString(), any(), anyLong(), any(), eq(200L));
    }

    @Test
    @DisplayName("checkAndConsume consumes exactly at limit boundary")
    void checkAndConsumeConsumesAtLimitBoundary() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(500L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 100L);

        assertThat(result.allowed()).isTrue();
        verify(repository).consume(anyString(), any(), anyLong(), any(), eq(100L));
    }

    @Test
    @DisplayName("checkAndConsume uses override limit when present")
    void checkAndConsumeUsesOverrideLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.of(1000L));
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(500L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 400L);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(1000L);
        verify(repository).consume(anyString(), any(), anyLong(), any(), eq(400L));
    }

    @Test
    @DisplayName("checkAndConsume reports consumed amount in result")
    void checkAndConsumeReportsRequestedAmount() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(100L);

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 123L);

        assertThat(result.requested()).isEqualTo(123L);
    }

    // ============ summary() tests ============

    @Test
    @DisplayName("summary includes both model types")
    void summaryIncludesBothModelTypes() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.empty());
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(100L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        assertThat(summary.limits()).hasSize(2);
    }

    @Test
    @DisplayName("summary returns remaining amount calculated from limit minus used")
    void summaryReturnsRemainingAmount() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.empty());
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        assertThat(summary.limits()).allMatch(item -> item.remaining() >= 0);
    }

    @Test
    @DisplayName("summary includes window reset time")
    void summaryIncludesWindowResetTime() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.empty());
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        assertThat(summary.windowResetAt()).isNotNull();
        assertThat(summary.windowSeconds()).isEqualTo(12 * 60 * 60); // 12 hours in seconds
    }

    @Test
    @DisplayName("summary includes correct unit in items")
    void summaryIncludesCorrectUnit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.TOKENS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.empty());
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        assertThat(summary.limits()).allMatch(item -> item.unit() == RequestRateLimitUnit.TOKENS);
    }

    // ============ unit() tests ============

    @Test
    @DisplayName("unit() returns configured unit")
    void unitReturnsConfiguredUnit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.TOKENS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);

        assertThat(service.unit()).isEqualTo(RequestRateLimitUnit.TOKENS);
    }

    @Test
    @DisplayName("unit() returns WORDS when configured")
    void unitReturnsWordsWhenConfigured() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);

        assertThat(service.unit()).isEqualTo(RequestRateLimitUnit.WORDS);
    }

    // ============ TEXT_MODEL multiplier tests ============

    @Test
    @DisplayName("defaultTextModelLimitMatchesSpeechModelLimit when multiplier is 0")
    void defaultTextModelLimitMatchesSpeechModelLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            0, // multiplier = 0 means 0
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit(anyString(), any())).thenReturn(Optional.empty());
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        RequestRateLimitSummaryItem speechLimit = limit(summary, ModelType.SPEECH_MODEL);
        assertThat(speechLimit.limit()).isEqualTo(600L);
    }

    @Test
    @DisplayName("text model limit multiplies speech model limit")
    void textModelLimitMultipliesSpeechModelLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            2, // multiplier = 2
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit("user-1", ModelType.TEXT_MODEL)).thenReturn(Optional.empty());
        when(repository.findOverrideLimit("user-1", ModelType.SPEECH_MODEL)).thenReturn(Optional.empty());
        when(repository.usedAmount("user-1", ModelType.TEXT_MODEL, 41160L)).thenReturn(0L);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        assertThat(limit(summary, ModelType.TEXT_MODEL).limit()).isEqualTo(1200L); // 600 * 2
        assertThat(limit(summary, ModelType.SPEECH_MODEL).limit()).isEqualTo(600L);
    }

    @Test
    @DisplayName("override limit bypasses text model multiplier")
    void overrideLimitBypassesTextModelMultiplier() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            2,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.findOverrideLimit("user-1", ModelType.TEXT_MODEL)).thenReturn(Optional.of(999L));
        when(repository.findOverrideLimit("user-1", ModelType.SPEECH_MODEL)).thenReturn(Optional.empty());
        when(repository.usedAmount("user-1", ModelType.TEXT_MODEL, 41160L)).thenReturn(0L);
        when(repository.usedAmount("user-1", ModelType.SPEECH_MODEL, 41160L)).thenReturn(0L);

        RequestRateLimitSummaryResponse summary = service.summary(testUser);

        // Should use override (999) instead of 600 * 2 (1200)
        assertThat(limit(summary, ModelType.TEXT_MODEL).limit()).isEqualTo(999L);
    }

    // ============ transactional behavior tests ============

    @Test
    @DisplayName("checkAndConsume passes correct unit to consume")
    void checkAndConsumePassesCorrectParametersToConsume() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.TOKENS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(50L);

        service.checkAndConsume(testUser, ModelType.TEXT_MODEL, 100L);

        verify(repository).consume(
            anyString(),
            any(),
            anyLong(),
            eq(RequestRateLimitUnit.TOKENS),
            eq(100L)
        );
    }

    // ============ remaining amount calculation tests ============

    @Test
    @DisplayName("remaining amount is never negative in checkAndConsume result")
    void remainingIsNeverNegativeWhenOverLimit() {
        RequestRateLimitProperties properties = new RequestRateLimitProperties(
            true,
            Duration.ofHours(12),
            600,
            1,
            RequestRateLimitUnit.WORDS
        );
        service = new RequestRateLimitService(properties, repository, new ChatUsageWindowCalculator(), clock);
        when(repository.usedAmount(anyString(), any(), anyLong())).thenReturn(700L); // Over limit

        RequestRateLimitResult result = service.checkAndConsume(testUser, ModelType.SPEECH_MODEL, 1L);

        assertThat(result.remaining()).isGreaterThanOrEqualTo(0L);
    }

    // ============ Helper methods ============

    private RequestRateLimitSummaryItem limit(RequestRateLimitSummaryResponse summary, ModelType modelType) {
        return summary.limits().stream()
            .filter(item -> item.modelType() == modelType)
            .findFirst()
            .orElseThrow();
    }
}
