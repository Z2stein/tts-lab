package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.workflow.SpeakerVoice;

public record SpeakerVoiceAnalysisItem(
    String speakerName,
    String roleDescription,
    SpeakerVoice voiceSuggestion
) {

}

