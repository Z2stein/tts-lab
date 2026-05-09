package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudioAssetResponse(
    String id,
    String sceneId,
    AudioAssetType type,
    int version,
    String filename,
    String contentType,
    long sizeBytes,
    Integer durationSeconds,
    AudioAssetStatus status,
    Instant createdAt,
    String downloadUrl,
    String streamUrl
) {
}
