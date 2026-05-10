package com.example.ttslab.audiobooks;

import java.time.Instant;

public record RenderSegment(
    String id,
    String aiGenerationRunId,
    String speechSegmentId,
    String text,
    String providerRequestJson,
    RunStatus status,
    String audioAssetId,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt
) {
}
