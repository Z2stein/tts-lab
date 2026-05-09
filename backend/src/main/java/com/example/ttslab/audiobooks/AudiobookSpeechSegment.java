package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudiobookSpeechSegment(
    String id,
    String projectId,
    int orderIndex,
    String title,
    AudiobookSpeechSegmentReviewStatus reviewStatus,
    Integer durationSeconds,
    Instant createdAt,
    Instant updatedAt,
    String speakerName,
    String speakerRoleDescription,
    String voiceName,
    String performanceDirections
) {
}
