package com.example.ttslab.projects.ttsworkbench;

import java.util.List;

public record SpeakerSplitAnalysisRequest(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers) {
}
