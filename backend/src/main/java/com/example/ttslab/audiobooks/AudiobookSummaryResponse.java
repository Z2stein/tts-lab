package com.example.ttslab.audiobooks;

import java.time.Instant;
import java.util.List;

public record AudiobookSummaryResponse(List<AudiobookSummaryItem> items) {
    public record AudiobookSummaryItem(
        String id,
        String title,
        AudiobookProjectStatus status,
        int sceneCount,
        Integer speakerCount,
        Integer totalDurationSeconds,
        Instant updatedAt,
        List<AudioAssetResponse> audioAssets
    ) {
    }
}
