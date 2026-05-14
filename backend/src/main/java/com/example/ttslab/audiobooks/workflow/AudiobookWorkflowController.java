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
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
            var project = audiobookProjectCreationService.createProject(user.id(), analysisResponse.projectTitle(), request.rawDialogue());
            speakerVoiceAnalysisService.syncProjectCharacters(project.getId(), analysisResponse.speakers());
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return new SpeakerVoiceAnalysisResponse(analysisResponse.speakers(), project.getId(), analysisResponse.projectTitle());
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
        Authentication authentication
    ) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        SingleSpeakerRenderPlanResponse requestPlan = renderPlanPersistenceService.loadRenderPlanFromDatabase(projectId);
        String promptText = renderPromptText(requestPlan);
        String providerModelName = renderProviderModelName(requestPlan);
        enforceLimit(user, ModelType.SPEECH_MODEL, promptText, providerModelName);
        try {

            List<SingleSpeakerRenderRequest> renderRequests = requestPlan.renderRequests() == null ? List.of() : requestPlan.renderRequests();
            if (renderRequests.isEmpty()) {
                throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "TTS_AUDIO_RENDER_REQUESTS_REQUIRED",
                    "At least one render request is required to create audio."
                );
            }

            AudiobookProject project = audiobookLibraryService.getProjectForUser(projectId, user);
            audiobookWorkflowStateService.ensureAudioGenerationReady(project);

            List<AudiobookSpeechSegment> previewSegments = audiobookLibraryService.preparePreviewSegments(project, renderRequests, false);
            List<byte[]> audioBytes = new ArrayList<>();
            TtsAudioFile firstAudioPart = null;
            for (int i = 0; i < renderRequests.size(); i++) {
                TtsAudioFile audioPart = audiobookWorkflowService.createAudio(renderRequests.get(i));
                if (firstAudioPart == null) firstAudioPart = audioPart;

                audioBytes.add(audioPart.content());
                Integer partDuration = DurationEstimator.estimateSpeakingDurationSeconds(stringValue(renderRequests.get(i).input(), "text"));
                audiobookLibraryService.persistAudioAsset(project, audioPart, previewSegments.get(i), 1, partDuration);
            }

            byte[] mergedAudio = mergeMp3Parts(audioBytes);
            String filename = renderRequests.size() == 1 ? firstAudioPart.filename() : "tts-render-plan.mp3";
            TtsAudioFile audioFile = new TtsAudioFile(mergedAudio, firstAudioPart.contentType(), filename);
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

    @PostMapping("/projects/{projectId}/audio-generated")
    public AudiobookWorkflowSnapshotResponse finalizeAudioGeneration(@PathVariable String projectId, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        return audiobookWorkflowStateService.finalizeAudioGeneration(user, projectId);
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

    private byte[] mergeMp3Parts(List<byte[]> audioParts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] audioPart : audioParts) {
            output.writeBytes(audioPart);
        }
        return output.toByteArray();
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
