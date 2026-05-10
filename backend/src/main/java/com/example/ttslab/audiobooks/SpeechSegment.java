package com.example.ttslab.audiobooks;

import java.time.Instant;

public record SpeechSegment(
    String id,
    String projectId,
    String characterId,
    int sequenceNo,
    String originalText,
    String annotatedText,
    boolean edited,
    boolean approved,
    Instant createdAt,
    Instant updatedAt
) {
}
