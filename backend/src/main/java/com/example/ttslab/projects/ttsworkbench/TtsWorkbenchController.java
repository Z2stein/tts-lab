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
    public SpeakerVoiceAnalysisResponse analyzeSpeakers(@RequestBody SpeakerVoiceAnalysisRequest request) {
        log.debug("/speaker-voice-analysis will send request "+request.toString());
        return ttsWorkbenchService.analyze(request.rawDialogue());
    }

    @PostMapping("/speaker-split-analysis")
    public SpeakerSplitAnalysisResponse splitDialogue(@RequestBody SpeakerSplitAnalysisRequest request) {
        return ttsWorkbenchService.split(request.rawDialogue(), request.speakers());
    }

    @PostMapping("/emotion-annotation-analysis")
    public EmotionAnnotationAnalysisResponse annotateEmotions(@RequestBody EmotionAnnotationAnalysisRequest request) {
        return ttsWorkbenchService.annotate(request.turns());
    }

    @PostMapping("/final-request-preview")
    public FinalTtsRequestPreviewResponse previewFinalRequest(@RequestBody FinalTtsRequestPreviewRequest request) {
        return ttsWorkbenchService.buildFinalRequest(request);
    }

    @PostMapping("/provider-compatible-request-plan")
    public ProviderCompatibleRequestPlanResponse previewProviderCompatibleRequestPlan(
        @RequestBody ProviderCompatibleRequestPlanRequest request
    ) {
        return ttsWorkbenchService.planProviderCompatibleRequests(request);
    }
}
