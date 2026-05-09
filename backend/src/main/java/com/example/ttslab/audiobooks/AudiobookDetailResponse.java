package com.example.ttslab.audiobooks;

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
    List<AudiobookSceneResponse> scenes,
    List<AudioAssetResponse> audioAssets
) {
}
