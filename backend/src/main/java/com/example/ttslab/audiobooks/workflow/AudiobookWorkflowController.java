package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisRequest;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.common.DurationEstimator;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.service.EmotionAnnotationPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.RenderPlanPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.ScriptPreviewWorkflowService;
import com.example.ttslab.audiobooks.workflow.service.SpeakerSplitPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.AudiobookWorkflowService;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.PromptRequestStatus;
import com.example.ttslab.ratelimit.RequestRateLimitExceededException;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audiobooks/workflow")
public class AudiobookWorkflowController {
    private static final Logger log = LoggerFactory.getLogger(AudiobookWorkflowController.class);

    private final AudiobookWorkflowService audiobookWorkflowService;
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final SpeakerSplitPersistenceService speakerSplitPersistenceService;
    private final EmotionAnnotationPersistenceService emotionAnnotationPersistenceService;
    private final RenderPlanPersistenceService renderPlanPersistenceService;
    private final ScriptPreviewWorkflowService scriptPreviewWorkflowService;
    private final AudiobookProjectCreationService audiobookProjectCreationService;
    private final AudiobookWorkflowStateService audiobookWorkflowStateService;
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
        RenderPlanPersistenceService renderPlanPersistenceService,
        ScriptPreviewWorkflowService scriptPreviewWorkflowService,
        AudiobookProjectCreationService audiobookProjectCreationService,
        AudiobookWorkflowStateService audiobookWorkflowStateService,
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
        this.renderPlanPersistenceService = renderPlanPersistenceService;
        this.scriptPreviewWorkflowService = scriptPreviewWorkflowService;
        this.audiobookProjectCreationService = audiobookProjectCreationService;
        this.audiobookWorkflowStateService = audiobookWorkflowStateService;
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
        this.requestRateLimitService = requestRateLimitService;
        this.requestUsageMeasurer = requestUsageMeasurer;
        this.audiobookLibraryService = audiobookLibraryService;
        this.analysisProviderModelName = providerModelName(chatbotProperties == null ? "mock" : chatbotProperties.provider(), chatModelName);
    }

    @GetMapping("/voices")
    public List<SpeakerVoiceCatalogItemResponse> getVoiceCatalog() {
        return Stream.of(SpeakerVoice.values())
                .map(SpeakerVoiceCatalogItemResponse::from)
                .toList();
    }

    @GetMapping("/projects/{projectId}")
    public AudiobookWorkflowSnapshotResponse getProjectSnapshot(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.snapshot(user, projectId);
    }

    @PostMapping("/speaker-voice-analysis")
    public SpeakerVoiceAnalysisResponse analyzeSpeakers(@RequestBody SpeakerVoiceAnalysisRequest request, Authentication authentication) {
        log.debug("/speaker-voice-analysis will send request {}", request);
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            SpeakerVoiceAnalysisResponse analysisResponse = speakerVoiceAnalysisService.analyze(request.rawDialogue());
            var project = audiobookProjectCreationService.createProjectWithSpeakers(
                user.id(),
                analysisResponse.projectTitle(),
                request.rawDialogue(),
                analysisResponse.sourceLanguageCode(),
                analysisResponse.productionLanguageCode(),
                analysisResponse.speakers()
            );
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return new SpeakerVoiceAnalysisResponse(
                analysisResponse.speakers(),
                project.getId(),
                analysisResponse.projectTitle(),
                analysisResponse.sourceLanguageCode(),
                analysisResponse.productionLanguageCode()
            );
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/projects/{projectId}/cast-approval")
    public AudiobookWorkflowSnapshotResponse approveCast(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.approveCast(user, projectId);
    }

    @PatchMapping("/projects/{projectId}/cast")
    public AudiobookWorkflowSnapshotResponse updateCast(
        @PathVariable String projectId,
        @Valid @RequestBody AudiobookWorkflowCastUpdateRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.updateCast(user, projectId, request.speakers());
    }

    @PostMapping("/speaker-split-analysis")
    public SpeakerSplitAnalysisResponse splitDialogue(@Valid @RequestBody SpeakerSplitAnalysisRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
            audiobookWorkflowStateService.ensureScriptReviewReady(project);
            SpeakerSplitAnalysisResponse response = speakerSplitPersistenceService.splitAndPersist(project, request.rawDialogue(), request.speakers());
            audiobookWorkflowStateService.markScriptReview(project);
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return response;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/projects/{projectId}/script-approval")
    public AudiobookWorkflowSnapshotResponse approveScript(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.approveScript(user, projectId);
    }

    @PostMapping("/emotion-annotation-analysis")
    public AudiobookWorkflowSnapshotResponse annotateEmotions(@Valid @RequestBody EmotionAnnotationAnalysisRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
        audiobookWorkflowStateService.ensurePerformanceNotesReady(project);
        var turns = emotionAnnotationPersistenceService.loadScriptPreviewTurns(project);
        EmotionAnnotationAnalysisResponse response = audiobookWorkflowService.annotate(turns);
        emotionAnnotationPersistenceService.persistStyledText(project, response.turns());
        audiobookWorkflowStateService.markPerformanceReady(project);
        return audiobookWorkflowStateService.snapshot(user, request.projectId());
    }

    @PostMapping("/script-preview-save")
    public SpeakerSplitAnalysisResponse saveScriptPreview(@Valid @RequestBody ScriptPreviewSaveRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return scriptPreviewWorkflowService.saveScriptPreview(user, request);
    }

    @PatchMapping("/projects/{projectId}/production-settings")
    public AudiobookWorkflowSnapshotResponse updateProductionSettings(
        @PathVariable String projectId,
        @Valid @RequestBody AudiobookWorkflowProductionSettingsRequest request,
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.updateProductionSettings(
            user,
            projectId,
            new AudiobookWorkflowProductionSettings(request.prompt(), request.languageCode(), request.modelName(), request.audioEncoding())
        );
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
            @RequestParam String projectId,
            @RequestParam int targetSegmentIndex,
            Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);

        AudiobookProject project = renderPlanPersistenceService.loadProjectFromDatabase(projectId);
        if (targetSegmentIndex < 0 || targetSegmentIndex >= project.getSpeechSegments().size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_SEGMENT_INDEX",
                "The requested segment index is out of bounds."
            );
        }
        enforceLimit(user, ModelType.SPEECH_MODEL, project,targetSegmentIndex);
        try {
            audiobookWorkflowStateService.ensureAudioGenerationReady(project);
            TtsAudioFile audioPart = audiobookWorkflowService.createAudio(project, targetSegmentIndex);
            AudiobookSpeechSegment segment = project.getSpeechSegments().get(targetSegmentIndex);
            Integer partDuration = DurationEstimator.estimateSpeakingDurationSeconds(segment.getStyledText());
            audiobookLibraryService.persistAudioAsset(project, audioPart, segment, 1, partDuration);
            promptHistoryService.record(user, ModelType.SPEECH_MODEL, project.getProductionModelName(), project.getStoryText(), PromptRequestStatus.SUCCESS);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + audioPart.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, audioPart.contentType())
                .header("X-Audiobook-Project-Id", project.getId())
                .body(audioPart.content());
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.SPEECH_MODEL, project.getProductionModelName(), project.getStoryText(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/projects/{projectId}/audio-generated")
    public AudiobookWorkflowSnapshotResponse finalizeAudioGeneration(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.finalizeAudioGeneration(user, projectId);
    }

    private void enforceLimit(CurrentUser user, ModelType modelType, AudiobookProject project,int targetSegmentIndex) {
        String promptText = project.getSpeechSegments().get(targetSegmentIndex).getStyledText();
        RequestRateLimitResult result = requestRateLimitService.checkAndConsume(
                user,
                modelType,
                requestUsageMeasurer.measure(promptText, requestRateLimitService.unit())
        );
        if (!result.allowed()) {
            promptHistoryService.record(user, modelType, project.getProductionModelName(), promptText, PromptRequestStatus.RATE_LIMITED);
            throw new RequestRateLimitExceededException(result);
        }
    }

    @Deprecated //use AudiobookProject based
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

    private List<SpeakerVoiceAnalysisItem> generatedSpeakerItems(List<SingleSpeakerRenderRequest> renderRequests) {
        LinkedHashMap<String, SpeakerVoiceAnalysisItem> speakersByKey = new LinkedHashMap<>();
        SpeakerVoice[] voices = SpeakerVoice.values();
        for (SingleSpeakerRenderRequest renderRequest : renderRequests) {
            String speakerName = stringValue(renderRequest.voice(), "speakerName", "speaker", "name");
            if (speakerName.isBlank()) {
                continue;
            }
            String normalizedSpeakerName = speakerName.trim().toLowerCase(Locale.ROOT);
            if (!speakersByKey.containsKey(normalizedSpeakerName)) {
                int voiceIndex = speakersByKey.size() % voices.length;
                speakersByKey.put(
                    normalizedSpeakerName,
                    new SpeakerVoiceAnalysisItem(speakerName.trim(), "Generated from audio render plan.", voices[voiceIndex])
                );
            }
        }
        return new ArrayList<>(speakersByKey.values());
    }

    private String stringValue(java.util.Map<String, Object> values, String... keys) {
        if (values == null) {
            return "";
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
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
