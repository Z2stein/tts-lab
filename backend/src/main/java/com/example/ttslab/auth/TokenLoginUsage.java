package com.example.ttslab.auth;

import java.time.Instant;

public record TokenLoginUsage(
    long id,
    String tokenJti,
    String tokenName,
    Instant usedAt
) {
}
