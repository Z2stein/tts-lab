package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ChatRateLimitServiceTest {

    @Test
    void allowsUntilMaxRequestsThenBlocks() {
        ChatRateLimitProperties props = new ChatRateLimitProperties(true, java.time.Duration.ofHours(1), 2, "X-User-Id");
        ChatUsageCounterStore store = new InMemoryChatUsageCounterStore();
        ChatUsageWindowCalculator windowCalculator = new ChatUsageWindowCalculator();
        Clock clock = Clock.fixed(Instant.parse("2026-05-05T10:15:30Z"), ZoneOffset.UTC);

        ChatRateLimitService service = new ChatRateLimitService(props, store, windowCalculator, clock);

        ChatRateLimitResult first = service.checkAndConsume("u1");
        ChatRateLimitResult second = service.checkAndConsume("u1");
        ChatRateLimitResult third = service.checkAndConsume("u1");

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertFalse(third.allowed());
        assertEquals(2670, third.retryAfterSeconds());
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        ChatRateLimitProperties props = new ChatRateLimitProperties(false, java.time.Duration.ofHours(1), 1, "X-User-Id");
        ChatRateLimitService service = new ChatRateLimitService(
            props,
            new InMemoryChatUsageCounterStore(),
            new ChatUsageWindowCalculator(),
            Clock.fixed(Instant.parse("2026-05-05T10:15:30Z"), ZoneOffset.UTC)
        );

        assertTrue(service.checkAndConsume("u1").allowed());
        assertTrue(service.checkAndConsume("u1").allowed());
    }

    @Test
    void newWindowResetsUsage() {
        ChatRateLimitProperties props = new ChatRateLimitProperties(true, java.time.Duration.ofSeconds(10), 1, "X-User-Id");
        ChatUsageCounterStore store = new InMemoryChatUsageCounterStore();
        ChatUsageWindowCalculator calculator = new ChatUsageWindowCalculator();

        ChatRateLimitService s1 = new ChatRateLimitService(props, store, calculator,
            Clock.fixed(Instant.parse("2026-05-05T10:00:01Z"), ZoneOffset.UTC));
        assertTrue(s1.checkAndConsume("u1").allowed());
        assertFalse(s1.checkAndConsume("u1").allowed());

        ChatRateLimitService s2 = new ChatRateLimitService(props, store, calculator,
            Clock.fixed(Instant.parse("2026-05-05T10:00:12Z"), ZoneOffset.UTC));
        assertTrue(s2.checkAndConsume("u1").allowed());
    }
}
