package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudiobookProject(
    String id,
    String userId,
    String title,
    AudiobookProjectStatus status,
    String sourceType,
    int sceneCount,
    Integer speakerCount,
    Integer totalDurationSeconds,
    Instant createdAt,
    Instant updatedAt
) {
}
