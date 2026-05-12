package com.example.ttslab.audiobooks.dto;

import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudioAssetResponse;

import java.time.Instant;
import java.util.List;

public record AudiobookSummaryResponse(List<AudiobookSummaryItem> items) {
    public record AudiobookSummaryItem(
        String id,
        String title,
        AudiobookProjectStatus status,
        int speechSegmentCount,
        Integer speakerCount,
        Integer totalDurationSeconds,
        Instant updatedAt,
        List<AudioAssetResponse> audioAssets
    ) {
    }
}
