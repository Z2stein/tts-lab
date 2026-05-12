package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisItem;

public interface TtsWorkbenchPromptProvider {
    String getSpeakerVoiceAnalysisPrompt(String rawDialogue);

    String getSpeakerSplitPrompt(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers);

    String getEmotionAnnotationPrompt(List<SpeakerSplitTurn> turns);
}
