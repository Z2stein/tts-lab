package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudioAsset(
    String id,
    String projectId,
    String sceneId,
    AudioAssetType type,
    int version,
    String storageKey,
    String filename,
    String contentType,
    long sizeBytes,
    Integer durationSeconds,
    AudioAssetStatus status,
    Instant createdAt
) {
}
