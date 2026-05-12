package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudioAssetType;

import java.time.Instant;

public record AudioAssetResponse(
    String id,
    String speechSegmentId,
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
