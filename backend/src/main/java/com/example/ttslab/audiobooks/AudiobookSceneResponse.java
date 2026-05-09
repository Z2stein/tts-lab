package com.example.ttslab.audiobooks;

public record AudiobookSceneResponse(
    String id,
    int orderIndex,
    String title,
    AudiobookSceneReviewStatus reviewStatus,
    Integer durationSeconds
) {
}
