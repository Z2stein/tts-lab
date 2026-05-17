package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AudiobookWorkflowCastUpdateRequest(
    @NotNull List<SpeakerVoiceAnalysisItem> speakers
) {
}
