package com.example.ttslab.audiobooks.workflow;

import java.util.List;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;

public interface AudiobookWorkflowPromptProvider {
    String getSpeakerVoiceAnalysisPrompt(String rawDialogue);

    String getSpeakerSplitPrompt(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers);

    String getEmotionAnnotationPrompt(List<SpeakerSplitTurn> turns);
}


