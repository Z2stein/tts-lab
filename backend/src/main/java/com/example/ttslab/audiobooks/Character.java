package com.example.ttslab.audiobooks;

import java.time.Instant;

public record Character(
    String id,
    String projectId,
    String name,
    String roleDescription,
    String voiceKey,
    int sortOrder,
    boolean approved,
    Instant createdAt,
    Instant updatedAt
) {
}
