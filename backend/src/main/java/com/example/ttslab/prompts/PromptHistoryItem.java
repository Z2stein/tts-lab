package com.example.ttslab.prompts;

import java.time.Instant;

public record PromptHistoryItem(
    long id,
    String userId,
    String userEmail,
    ModelType modelType,
    String providerModelName,
    String promptText,
    PromptRequestStatus requestStatus,
    Instant createdAt
) {
}
