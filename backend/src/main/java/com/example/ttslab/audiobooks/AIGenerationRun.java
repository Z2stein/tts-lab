package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AIGenerationRun(
    String id,
    String projectId,
    RunType type,
    RunStatus status,
    String requestJson,
    String responseJson,
    String errorMessage,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt
) {
}
