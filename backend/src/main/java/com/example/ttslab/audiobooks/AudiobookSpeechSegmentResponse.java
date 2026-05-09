package com.example.ttslab.audiobooks;

public record AudiobookSpeechSegmentResponse(
    String id,
    int orderIndex,
    String title,
    AudiobookSpeechSegmentReviewStatus reviewStatus,
    Integer durationSeconds,
    String speakerName,
    String speakerRoleDescription,
    String voiceName,
    String performanceDirections
) {
}
