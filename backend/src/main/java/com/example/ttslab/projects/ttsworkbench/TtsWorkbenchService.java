package com.example.ttslab.projects.ttsworkbench;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TtsWorkbenchService {
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitAnalysisService speakerSplitAnalysisService;
    private final EmotionAnnotationService emotionAnnotationService;
    private final FinalTtsRequestBuilder finalTtsRequestBuilder;
    private final ProviderCompatibleRequestPlanner providerCompatibleRequestPlanner;

    public TtsWorkbenchService(
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService,
        SpeakerSplitAnalysisService speakerSplitAnalysisService,
        EmotionAnnotationService emotionAnnotationService,
        FinalTtsRequestBuilder finalTtsRequestBuilder,
        ProviderCompatibleRequestPlanner providerCompatibleRequestPlanner
    ) {
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
        this.speakerSplitAnalysisService = speakerSplitAnalysisService;
        this.emotionAnnotationService = emotionAnnotationService;
        this.finalTtsRequestBuilder = finalTtsRequestBuilder;
        this.providerCompatibleRequestPlanner = providerCompatibleRequestPlanner;
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

    public ProviderCompatibleRequestPlanResponse planProviderCompatibleRequests(ProviderCompatibleRequestPlanRequest request) {
        return providerCompatibleRequestPlanner.plan(request);
    }
}
