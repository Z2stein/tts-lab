package com.example.ttslab.audiobooks.workflow;

import java.util.List;

public record EmotionAnnotationAnalysisResponse(List<AnnotatedSpeakerTurn> turns) {
}

