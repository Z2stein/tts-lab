package com.example.ttslab.audiobooks.dto;

import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudioAssetResponse;

import java.time.Instant;
import java.util.List;

public record AudiobookDetailResponse(
    String id,
    String title,
    AudiobookProjectStatus status,
    int speechSegmentCount,
    Integer speakerCount,
    Integer totalDurationSeconds,
    Instant createdAt,
    Instant updatedAt,
    List<AudiobookSpeechSegmentResponse> speechSegments,
    List<AudioAssetResponse> audioAssets
) {
}
