package com.example.ttslab.audiobooks.workflow;

import jakarta.validation.constraints.NotBlank;

public record AudiobookWorkflowProductionSettingsRequest(
    @NotBlank String prompt,
    @NotBlank String languageCode,
    @NotBlank String modelName,
    @NotBlank String audioEncoding
) {
}
