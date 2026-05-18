package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class AudiobookCreationService {

    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;

    public AudiobookCreationService(
            SpeakerVoiceAnalysisService speakerVoiceAnalysisService
    ) {
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue, String customHint) {
        return speakerVoiceAnalysisService.analyze(rawDialogue, customHint);
    }
}

