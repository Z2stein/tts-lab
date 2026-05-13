package com.example.ttslab.audiobooks.workflow;

import java.util.List;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import jakarta.validation.constraints.NotBlank;

public record SpeakerSplitAnalysisRequest(
    String rawDialogue,
    List<SpeakerVoiceAnalysisItem> speakers,
    @NotBlank String projectId
) {
}

