package com.example.ttslab.ratelimit;

import com.example.ttslab.prompts.ModelType;

public record RequestRateLimitResult(
    ModelType modelType,
    boolean allowed,
    long used,
    long limit,
    long remaining,
    long requested,
    long retryAfterSeconds,
    long windowBucket,
    RequestRateLimitUnit unit
) {
}

