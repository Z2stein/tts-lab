package com.example.ttslab.audiobooks.workflow;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ScriptPreviewSaveRequest(@NotBlank String projectId, List<SpeakerSplitTurn> turns) {
}

