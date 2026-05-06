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
}
