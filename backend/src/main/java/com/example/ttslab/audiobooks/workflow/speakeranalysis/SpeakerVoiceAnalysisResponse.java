package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import java.util.List;

public record SpeakerVoiceAnalysisResponse(
    List<SpeakerVoiceAnalysisItem> speakers,
    String projectId,
    String projectTitle,
    String languageCode
) {
}

