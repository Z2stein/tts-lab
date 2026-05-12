package com.example.ttslab.projects.ttsworkbench;

import jakarta.validation.constraints.NotBlank;

public record EmotionAnnotationAnalysisRequest(@NotBlank String projectId) {
}
