package com.example.ttslab.audiobooks.wf.speakeranalysis;

import com.example.ttslab.projects.ttsworkbench.SpeakerVoice;

public record SpeakerVoiceAnalysisItem(
    String speakerName,
    String roleDescription,
    SpeakerVoice voiceSuggestion
) {

}
