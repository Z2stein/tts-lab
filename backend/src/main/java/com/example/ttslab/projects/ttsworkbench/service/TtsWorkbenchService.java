package com.example.ttslab.projects.ttsworkbench.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.ttslab.projects.ttsworkbench.*;
import org.springframework.stereotype.Service;

@Service
public class TtsWorkbenchService {
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitAnalysisService speakerSplitAnalysisService;
    private final EmotionAnnotationService emotionAnnotationService;
    private final FinalTtsRequestBuilder finalTtsRequestBuilder;
    private final SingleSpeakerRenderPlanner singleSpeakerRenderPlanner;
    private final TtsAudioCreationService ttsAudioCreationService;
    private final AudiobookProjectRepository audiobookProjectRepository;

    public TtsWorkbenchService(
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService,
        SpeakerSplitAnalysisService speakerSplitAnalysisService,
        EmotionAnnotationService emotionAnnotationService,
        FinalTtsRequestBuilder finalTtsRequestBuilder,
        SingleSpeakerRenderPlanner singleSpeakerRenderPlanner,
        TtsAudioCreationService ttsAudioCreationService,
        AudiobookProjectRepository audiobookProjectRepository
    ) {
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
        this.speakerSplitAnalysisService = speakerSplitAnalysisService;
        this.emotionAnnotationService = emotionAnnotationService;
        this.finalTtsRequestBuilder = finalTtsRequestBuilder;
        this.singleSpeakerRenderPlanner = singleSpeakerRenderPlanner;
        this.ttsAudioCreationService = ttsAudioCreationService;
        this.audiobookProjectRepository = audiobookProjectRepository;
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        return speakerVoiceAnalysisService.analyze(rawDialogue);
    }

    public SpeakerVoiceAnalysisResponse analyzeAndCreateProject(String rawDialogue, String userId) {
        SpeakerVoiceAnalysisResponse analysisResponse = speakerVoiceAnalysisService.analyze(rawDialogue);

        if (analysisResponse == null || analysisResponse.speakers() == null) {
            return analysisResponse;
        }

        try {
            String projectId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            String title = generateProjectTitle(rawDialogue);
            int speakerCount = analysisResponse.speakers().size();

            AudiobookProject project = new AudiobookProject(
                projectId,
                userId,
                title,
                AudiobookProjectStatus.DRAFT,
                "voice_analysis",
                0,
                speakerCount,
                null,
                now,
                now
            );

            audiobookProjectRepository.save(project);

            return new SpeakerVoiceAnalysisResponse(analysisResponse.speakers(), projectId);
        } catch (Exception e) {
            // Log error but don't fail the response - analysis succeeded even if project creation failed
            return new SpeakerVoiceAnalysisResponse(analysisResponse.speakers(), null);
        }
    }

    private String generateProjectTitle(String rawDialogue) {
        int maxLength = 255;
        String prefix = "Analysis: ";
        String truncated = rawDialogue.substring(0, Math.min(rawDialogue.length(), maxLength - prefix.length()));
        return prefix + truncated.replace("\n", " ").replace("\r", "");
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
