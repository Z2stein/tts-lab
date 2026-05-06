package com.example.ttslab.projects.ttsworkbench;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/tts-workbench")
public class TtsWorkbenchController {
    private final TtsWorkbenchService ttsWorkbenchService;

    public TtsWorkbenchController(TtsWorkbenchService ttsWorkbenchService) {
        this.ttsWorkbenchService = ttsWorkbenchService;
    }

    @PostMapping("/speaker-voice-analysis")
    public SpeakerVoiceAnalysisResponse analyzeSpeakers(@RequestBody SpeakerVoiceAnalysisRequest request) {
        return ttsWorkbenchService.analyze(request.rawDialogue());
    }
}
