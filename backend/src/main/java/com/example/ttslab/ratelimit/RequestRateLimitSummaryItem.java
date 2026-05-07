package com.example.ttslab.ratelimit;

import com.example.ttslab.prompts.ModelType;

public record RequestRateLimitSummaryItem(
    ModelType modelType,
    long used,
    long limit,
    long remaining,
    RequestRateLimitUnit unit
) {
}

