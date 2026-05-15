package com.example.ttslab.audiobooks.workflow;

public record AudiobookWorkflowProductionSettings(
    String prompt,
    String languageCode,
    String modelName,
    String audioEncoding
) {
}
