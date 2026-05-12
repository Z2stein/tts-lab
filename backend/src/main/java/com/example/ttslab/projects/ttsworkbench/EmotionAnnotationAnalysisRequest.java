package com.example.ttslab.projects.ttsworkbench;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record EmotionAnnotationAnalysisRequest(@NotBlank String projectId, List<SpeakerSplitTurn> turns) {
}
