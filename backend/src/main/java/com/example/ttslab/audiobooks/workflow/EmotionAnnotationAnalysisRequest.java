package com.example.ttslab.audiobooks.workflow;

import jakarta.validation.constraints.NotBlank;

public record EmotionAnnotationAnalysisRequest(@NotBlank String projectId) {
}

