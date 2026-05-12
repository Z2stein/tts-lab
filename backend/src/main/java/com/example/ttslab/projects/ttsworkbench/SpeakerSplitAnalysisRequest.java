package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisItem;
import jakarta.validation.constraints.NotBlank;

public record SpeakerSplitAnalysisRequest(
    String rawDialogue,
    List<SpeakerVoiceAnalysisItem> speakers,
    @NotBlank String projectId
) {
}
