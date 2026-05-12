package com.example.ttslab.audiobooks.wf.speakeranalysis;

import java.util.List;

public record SpeakerVoiceAnalysisResponse(
    List<SpeakerVoiceAnalysisItem> speakers,
    String projectId
) {
}
