package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudiobookScene(
    String id,
    String projectId,
    int orderIndex,
    String title,
    AudiobookSceneReviewStatus reviewStatus,
    Integer durationSeconds,
    Instant createdAt,
    Instant updatedAt
) {
}
