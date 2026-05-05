package com.example.ttslab.chat;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatRateLimitService {
    private static final Logger log = LoggerFactory.getLogger(ChatRateLimitService.class);

    private final ChatRateLimitProperties properties;
    private final ChatUsageCounterStore counterStore;
    private final ChatUsageWindowCalculator windowCalculator;
    private final Clock clock;

    public ChatRateLimitService(ChatRateLimitProperties properties, ChatUsageCounterStore counterStore,
                                ChatUsageWindowCalculator windowCalculator, Clock clock) {
        this.properties = properties;
        this.counterStore = counterStore;
        this.windowCalculator = windowCalculator;
        this.clock = clock;
    }

    public ChatRateLimitResult checkAndConsume(String identifier) {
        if (!properties.enabled()) {
            return new ChatRateLimitResult(true, 0, 0, 0);
        }

        Instant now = clock.instant();
        long bucket = windowCalculator.windowBucket(now, properties.window());
        long count = counterStore.incrementAndGet(identifier, bucket);

        if (count <= properties.maxRequests()) {
            return new ChatRateLimitResult(true, count, 0, bucket);
        }

        long retryAfter = windowCalculator.retryAfterSeconds(now, properties.window());
        log.warn("Chat rate limit exceeded (identifier={}, windowBucket={}, currentCount={}, maxLimit={}, retryAfterSeconds={})",
            identifier, bucket, count, properties.maxRequests(), retryAfter);
        return new ChatRateLimitResult(false, count, retryAfter, bucket);
    }
}
