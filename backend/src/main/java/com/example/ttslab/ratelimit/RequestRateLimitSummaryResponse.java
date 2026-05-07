package com.example.ttslab.ratelimit;

import java.time.Instant;
import java.util.List;

public record RequestRateLimitSummaryResponse(
    Instant windowResetAt,
    long windowSeconds,
    List<RequestRateLimitSummaryItem> limits
) {
}

