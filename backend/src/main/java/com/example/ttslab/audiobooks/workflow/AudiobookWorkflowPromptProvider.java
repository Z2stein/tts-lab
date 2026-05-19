package com.example.ttslab.audiobooks.workflow;

import java.util.List;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;

public interface AudiobookWorkflowPromptProvider {
    String getSpeakerVoiceAnalysisPrompt(String rawDialogue, String customHint);

    String getSpeakerSplitPrompt(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers, String customHint);

    String getEmotionAnnotationPrompt(List<SpeakerSplitTurn> turns, String customHint);

    String getStoryDraftPrompt(String idea, List<String> enhancements);
}


