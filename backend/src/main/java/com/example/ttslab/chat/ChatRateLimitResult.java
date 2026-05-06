package com.example.ttslab.chat;

public record ChatRateLimitResult(boolean allowed, long currentCount, long retryAfterSeconds, long windowBucket) {
}
