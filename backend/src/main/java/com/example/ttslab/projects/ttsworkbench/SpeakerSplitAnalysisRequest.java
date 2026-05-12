package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisItem;

public record SpeakerSplitAnalysisRequest(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers) {
}
