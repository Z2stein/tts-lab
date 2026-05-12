package com.example.ttslab.projects.ttsworkbench.service;

import java.util.List;

import com.example.ttslab.projects.ttsworkbench.*;
import com.example.ttslab.audiobooks.wf.speakeranalysis.*;
import org.springframework.stereotype.Service;

@Service
public class TtsWorkbenchService {
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitAnalysisService speakerSplitAnalysisService;
    private final EmotionAnnotationService emotionAnnotationService;
    private final FinalTtsRequestBuilder finalTtsRequestBuilder;
    private final SingleSpeakerRenderPlanner singleSpeakerRenderPlanner;
    private final TtsAudioCreationService ttsAudioCreationService;

    public TtsWorkbenchService(
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService,
        SpeakerSplitAnalysisService speakerSplitAnalysisService,
        EmotionAnnotationService emotionAnnotationService,
        FinalTtsRequestBuilder finalTtsRequestBuilder,
        SingleSpeakerRenderPlanner singleSpeakerRenderPlanner,
        TtsAudioCreationService ttsAudioCreationService
    ) {
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
        this.speakerSplitAnalysisService = speakerSplitAnalysisService;
        this.emotionAnnotationService = emotionAnnotationService;
        this.finalTtsRequestBuilder = finalTtsRequestBuilder;
        this.singleSpeakerRenderPlanner = singleSpeakerRenderPlanner;
        this.ttsAudioCreationService = ttsAudioCreationService;
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        return speakerVoiceAnalysisService.analyze(rawDialogue);
    }

    public SpeakerSplitAnalysisResponse split(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers) {
        return speakerSplitAnalysisService.split(rawDialogue, speakers);
    }

    public EmotionAnnotationAnalysisResponse annotate(List<SpeakerSplitTurn> turns) {
        return emotionAnnotationService.annotate(turns);
    }

    public FinalTtsRequestPreviewResponse buildFinalRequest(FinalTtsRequestPreviewRequest request) {
        return finalTtsRequestBuilder.build(request);
    }

    public SingleSpeakerRenderPlanResponse planSingleSpeakerRenderRequests(SingleSpeakerRenderPlanRequest request) {
        return singleSpeakerRenderPlanner.plan(request);
    }

    public TtsAudioFile createAudio(SingleSpeakerRenderPlanResponse requestPlan) {
        return ttsAudioCreationService.createAudio(requestPlan);
    }
}
