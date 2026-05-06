package com.example.ttslab.projects.ttsworkbench;

import java.util.List;

public record FinalTtsRequestPreviewRequest(
    String prompt,
    List<SpeakerVoiceAnalysisItem> speakers,
    List<AnnotatedSpeakerTurn> annotatedTurns,
    String languageCode,
    String modelName,
    String audioEncoding
) {
}
