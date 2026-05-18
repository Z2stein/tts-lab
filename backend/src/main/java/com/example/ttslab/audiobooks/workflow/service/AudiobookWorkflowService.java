package com.example.ttslab.audiobooks.workflow.service;

import java.util.List;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.workflow.*;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.*;
import org.springframework.stereotype.Service;

@Service
public class AudiobookWorkflowService {
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitAnalysisService speakerSplitAnalysisService;
    private final EmotionAnnotationService emotionAnnotationService;
    private final FinalTtsRequestBuilder finalTtsRequestBuilder;
    private final SingleSpeakerRenderPlanner singleSpeakerRenderPlanner;
    private final TtsAudioCreationService ttsAudioCreationService;

    public AudiobookWorkflowService(
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

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue, String customHint) {
        return speakerVoiceAnalysisService.analyze(rawDialogue, customHint);
    }

    public SpeakerSplitAnalysisResponse split(String rawDialogue, List<SpeakerVoiceAnalysisItem> speakers, String customHint) {
        return speakerSplitAnalysisService.split(rawDialogue, speakers, customHint);
    }

    public EmotionAnnotationAnalysisResponse annotate(List<SpeakerSplitTurn> turns, String customHint) {
        return emotionAnnotationService.annotate(turns, customHint);
    }

    public FinalTtsRequestPreviewResponse buildFinalRequest(FinalTtsRequestPreviewRequest request) {
        return finalTtsRequestBuilder.build(request);
    }

    public SingleSpeakerRenderPlanResponse planSingleSpeakerRenderRequests(SingleSpeakerRenderPlanRequest request) {
        return singleSpeakerRenderPlanner.plan(request);
    }
    public TtsAudioFile createAudio(AudiobookProject project, int targetSegmentIndex) {
        return ttsAudioCreationService.createAudio(project, targetSegmentIndex);
    }
}


