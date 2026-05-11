package com.example.ttslab.audiobooks.dto;

import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudioAssetResponse;

import java.time.Instant;
import java.util.List;

public record AudiobookDetailResponse(
    String id,
    String title,
    AudiobookProjectStatus status,
    int sceneCount,
    Integer speakerCount,
    Integer totalDurationSeconds,
    Instant createdAt,
    Instant updatedAt,
    List<AudiobookSpeechSegmentResponse> scenes,
    List<AudioAssetResponse> audioAssets
) {
}
