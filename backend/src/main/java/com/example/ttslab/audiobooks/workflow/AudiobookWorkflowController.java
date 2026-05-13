package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisRequest;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.common.DurationEstimator;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.audiobooks.workflow.EmotionAnnotationAnalysisRequest;
import com.example.ttslab.audiobooks.workflow.EmotionAnnotationAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.FinalTtsRequestPreviewRequest;
import com.example.ttslab.audiobooks.workflow.FinalTtsRequestPreviewResponse;
import com.example.ttslab.audiobooks.workflow.ScriptPreviewSaveRequest;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderPlanRequest;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderPlanResponse;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisRequest;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.TtsAudioFile;
import com.example.ttslab.audiobooks.workflow.service.EmotionAnnotationPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.SpeakerSplitPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.AudiobookWorkflowService;
import com.example.ttslab.audiobooks.workflow.AudiobookProjectCreationService;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.PromptRequestStatus;
import com.example.ttslab.ratelimit.RequestRateLimitExceededException;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audiobooks/workflow")
public class AudiobookWorkflowController {
    private static final Logger log = LoggerFactory.getLogger(AudiobookWorkflowController.class);

    private final AudiobookWorkflowService audiobookWorkflowService;
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitPersistenceService speakerSplitPersistenceService;
    private final EmotionAnnotationPersistenceService emotionAnnotationPersistenceService;
    private final AudiobookProjectCreationService audiobookProjectCreationService;
    private final CurrentUserResolver currentUserResolver;
    private final PromptHistoryService promptHistoryService;
    private final RequestRateLimitService requestRateLimitService;
    private final RequestUsageMeasurer requestUsageMeasurer;
    private final AudiobookLibraryService audiobookLibraryService;
    private final String analysisProviderModelName;

    public AudiobookWorkflowController(
        AudiobookWorkflowService audiobookWorkflowService,
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService,
        SpeakerSplitPersistenceService speakerSplitPersistenceService,
        EmotionAnnotationPersistenceService emotionAnnotationPersistenceService,
        AudiobookProjectCreationService audiobookProjectCreationService,
        CurrentUserResolver currentUserResolver,
        PromptHistoryService promptHistoryService,
        RequestRateLimitService requestRateLimitService,
        RequestUsageMeasurer requestUsageMeasurer,
        AudiobookLibraryService audiobookLibraryService,
        ChatbotProperties chatbotProperties,
        @Value("${spring.ai.google.genai.chat.options.model:}") String chatModelName
    ) {
        this.audiobookWorkflowService = audiobookWorkflowService;
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
        this.speakerSplitPersistenceService = speakerSplitPersistenceService;
        this.emotionAnnotationPersistenceService = emotionAnnotationPersistenceService;
        this.audiobookProjectCreationService = audiobookProjectCreationService;
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
        this.requestRateLimitService = requestRateLimitService;
        this.requestUsageMeasurer = requestUsageMeasurer;
        this.audiobookLibraryService = audiobookLibraryService;
        this.analysisProviderModelName = providerModelName(chatbotProperties == null ? "mock" : chatbotProperties.provider(), chatModelName);
    }

    @PostMapping("/speaker-voice-analysis")
    public SpeakerVoiceAnalysisResponse analyzeSpeakers(@RequestBody SpeakerVoiceAnalysisRequest request, Authentication authentication) {
        log.debug("/speaker-voice-analysis will send request {}", request);
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            var project = audiobookProjectCreationService.createProject(user.id());
            SpeakerVoiceAnalysisResponse analysisResponse = speakerVoiceAnalysisService.analyze(request.rawDialogue(), project.getId());
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return analysisResponse;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/speaker-split-analysis")
    public SpeakerSplitAnalysisResponse splitDialogue(@Valid @RequestBody SpeakerSplitAnalysisRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
            SpeakerSplitAnalysisResponse response = speakerSplitPersistenceService.splitAndPersist(project, request.rawDialogue(), request.speakers());
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return response;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/emotion-annotation-analysis")
    public EmotionAnnotationAnalysisResponse annotateEmotions(@Valid @RequestBody EmotionAnnotationAnalysisRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
        var turns = emotionAnnotationPersistenceService.loadScriptPreviewTurns(project);
        EmotionAnnotationAnalysisResponse response = audiobookWorkflowService.annotate(turns);
        emotionAnnotationPersistenceService.persistStyledText(project, response.turns());
        return response;
    }

    @PostMapping("/script-preview-save")
    public SpeakerSplitAnalysisResponse saveScriptPreview(@Valid @RequestBody ScriptPreviewSaveRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
        return new SpeakerSplitAnalysisResponse(emotionAnnotationPersistenceService.saveScriptPreviewTurns(project, request.turns()));
    }

    @PostMapping("/final-request-preview")
    public FinalTtsRequestPreviewResponse previewFinalRequest(@RequestBody FinalTtsRequestPreviewRequest request) {
        return audiobookWorkflowService.buildFinalRequest(request);
    }

    @PostMapping("/single-speaker-render-plan")
    public SingleSpeakerRenderPlanResponse previewSingleSpeakerRenderPlan(@RequestBody SingleSpeakerRenderPlanRequest request) {
        return audiobookWorkflowService.planSingleSpeakerRenderRequests(request);
    }

    @PostMapping("/create-audio")
    public ResponseEntity<byte[]> createAudio(
        @RequestBody SingleSpeakerRenderPlanResponse requestPlan,
        @org.springframework.web.bind.annotation.RequestParam(required = false) String projectId,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        String promptText = renderPromptText(requestPlan);
        String providerModelName = renderProviderModelName(requestPlan);
        enforceLimit(user, ModelType.SPEECH_MODEL, promptText, providerModelName);
        try {
            Set<String> uniqueSpeakers = new HashSet<>();
            int segmentCount = 0;
            String firstSpeakerName = null;
            String firstVoiceName = null;

            if (requestPlan.renderRequests() != null && !requestPlan.renderRequests().isEmpty()) {
                var firstRequest = requestPlan.renderRequests().get(0);
                firstSpeakerName = stringValue(firstRequest.voice(), "speakerName");
                firstVoiceName = stringValue(firstRequest.voice(), "speakerId");

                for (var request : requestPlan.renderRequests()) {
                    segmentCount++;
                    String speaker = stringValue(request.voice(), "speakerName");
                    if (speaker != null && !speaker.isBlank()) {
                        uniqueSpeakers.add(speaker);
                    }
                }
            }

            int speakerCount = uniqueSpeakers.size();
            Integer estimatedDuration = DurationEstimator.estimateSpeakingDurationSeconds(promptText);

            TtsAudioFile audioFile = audiobookWorkflowService.createAudio(requestPlan);

            AudiobookProject project;
            if (projectId == null || projectId.isBlank()) {
                project = audiobookLibraryService.createProjectForGeneration(user);
            } else {
                project = audiobookLibraryService.getProjectForUser(projectId, user);
            }

            audiobookLibraryService.persistAudioAsset(project, audioFile, segmentCount, 1, speakerCount, estimatedDuration, firstSpeakerName, null, firstVoiceName, null);
            promptHistoryService.record(user, ModelType.SPEECH_MODEL, providerModelName, promptText, PromptRequestStatus.SUCCESS);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + audioFile.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, audioFile.contentType())
                .header("X-Audiobook-Project-Id", project.getId())
                .body(audioFile.content());
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.SPEECH_MODEL, providerModelName, promptText, PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    private void enforceLimit(CurrentUser user, ModelType modelType, String promptText, String providerModelName) {
        RequestRateLimitResult result = requestRateLimitService.checkAndConsume(
            user,
            modelType,
            requestUsageMeasurer.measure(promptText, requestRateLimitService.unit())
        );
        if (!result.allowed()) {
            promptHistoryService.record(user, modelType, providerModelName, promptText, PromptRequestStatus.RATE_LIMITED);
            throw new RequestRateLimitExceededException(result);
        }
    }

    private String renderPromptText(SingleSpeakerRenderPlanResponse requestPlan) {
        if (requestPlan == null || requestPlan.renderRequests() == null) {
            return "";
        }
        return requestPlan.renderRequests().stream()
            .map(request -> stringValue(request.input(), "text"))
            .filter(text -> !text.isBlank())
            .collect(Collectors.joining("\n\n"));
    }

    private String renderProviderModelName(SingleSpeakerRenderPlanResponse requestPlan) {
        if (requestPlan == null || requestPlan.renderRequests() == null) {
            return "google-tts";
        }
        return requestPlan.renderRequests().stream()
            .map(request -> stringValue(request.voice(), "modelName"))
            .filter(modelName -> !modelName.isBlank())
            .findFirst()
            .map(modelName -> "google-tts/" + modelName)
            .orElse("google-tts");
    }

    private String stringValue(java.util.Map<String, Object> values, String key) {
        if (values == null) {
            return "";
        }
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String providerModelName(String provider, String modelName) {
        String safeProvider = provider == null || provider.isBlank() ? "mock" : provider.trim();
        if ("mock".equalsIgnoreCase(safeProvider)) {
            return "mock";
        }
        if (modelName == null || modelName.isBlank()) {
            return safeProvider;
        }
        return safeProvider + "/" + modelName.trim();
    }
}


