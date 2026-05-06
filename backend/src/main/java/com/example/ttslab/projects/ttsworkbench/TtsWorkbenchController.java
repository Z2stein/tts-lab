package com.example.ttslab.projects.ttsworkbench;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/tts-workbench")
public class TtsWorkbenchController {

    private static final Logger log = LoggerFactory.getLogger(TtsWorkbenchController.class);
    private final TtsWorkbenchService ttsWorkbenchService;

    public TtsWorkbenchController(TtsWorkbenchService ttsWorkbenchService) {
        this.ttsWorkbenchService = ttsWorkbenchService;
    }

    @PostMapping("/speaker-voice-analysis")
    public SpeakerVoiceAnalysisResponse analyzeSpeakerVoices(@RequestBody SpeakerVoiceAnalysisRequest request) {
        int inputLength = request.rawDialogue() == null ? 0 : request.rawDialogue().length();
        log.info("POST /api/projects/tts-workbench/speaker-voice-analysis called (inputLength={})", inputLength);

        SpeakerVoiceAnalysisResponse response = ttsWorkbenchService.analyzeSpeakerVoices(request.rawDialogue());
        log.info("POST /api/projects/tts-workbench/speaker-voice-analysis succeeded (speakers={})", response.speakers().size());
        return response;
    }
}
