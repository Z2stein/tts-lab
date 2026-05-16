package com.example.ttslab.audiobooks.workflow;

import java.util.List;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;

public record FinalTtsRequestPreviewRequest(
    String prompt,
    List<SpeakerVoiceAnalysisItem> speakers,
    List<AnnotatedSpeakerTurn> annotatedTurns,
    String languageCode,
    String modelName,
    String audioEncoding
) {
}

