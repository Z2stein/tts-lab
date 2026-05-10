package com.example.ttslab.audiobooks;

import java.time.Instant;

public record AudiobookProject(
    String id,
    String userId,
    String title,
    String sourceText,
    String languageCode,
    String modelName,
    String audioEncoding,
    AudiobookProjectStatus status,
    int revision,
    Instant createdAt,
    Instant updatedAt
) {
}
