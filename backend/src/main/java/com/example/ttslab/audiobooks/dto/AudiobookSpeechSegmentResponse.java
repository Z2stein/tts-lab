package com.example.ttslab.audiobooks.dto;

import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;

public record AudiobookSpeechSegmentResponse(
    String id,
    int orderIndex,
    String title,
    AudiobookSpeechSegmentReviewStatus reviewStatus,
    Integer durationSeconds,
    String speakerName,
    String speakerRoleDescription,
    String voiceName,
    String performanceDirections,
    String styledText
) {
}
