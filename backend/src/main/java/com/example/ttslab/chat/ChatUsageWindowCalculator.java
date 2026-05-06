package com.example.ttslab.chat;

import java.time.Duration;
import java.time.Instant;

public class ChatUsageWindowCalculator {
    public long windowBucket(Instant instant, Duration window) {
        return instant.getEpochSecond() / window.getSeconds();
    }

    public long retryAfterSeconds(Instant instant, Duration window) {
        long windowSeconds = window.getSeconds();
        long elapsed = instant.getEpochSecond() % windowSeconds;
        return windowSeconds - elapsed;
    }
}
