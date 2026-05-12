package com.example.ttslab.audiobooks.wf;

import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class AudiobookCreationService {

    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;

    public AudiobookCreationService(
            SpeakerVoiceAnalysisService speakerVoiceAnalysisService
    ) {
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        return speakerVoiceAnalysisService.analyze(rawDialogue);
    }
}
