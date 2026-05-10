package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudioAsset(
    String id,
    String projectId,
    String aiGenerationRunId,
    AudioAssetType type,
    String fileName,
    String storageKey,
    String contentType,
    Long durationMs,
    long sizeBytes,
    Instant createdAt
) {
}
