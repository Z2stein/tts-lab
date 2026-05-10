package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.AudiobookLibraryService;
import com.example.ttslab.common.DurationEstimator;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.PromptRequestStatus;
import com.example.ttslab.ratelimit.RequestRateLimitExceededException;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/tts-workbench")
public class TtsWorkbenchController {
    private static final Logger log = LoggerFactory.getLogger(TtsWorkbenchController.class);
    private final TtsWorkbenchService ttsWorkbenchService;
    private final CurrentUserResolver currentUserResolver;
    private final PromptHistoryService promptHistoryService;
    private final RequestRateLimitService requestRateLimitService;
    private final RequestUsageMeasurer requestUsageMeasurer;
    private final AudiobookLibraryService audiobookLibraryService;
    private final String analysisProviderModelName;

    public TtsWorkbenchController(
        TtsWorkbenchService ttsWorkbenchService,
        CurrentUserResolver currentUserResolver,
        PromptHistoryService promptHistoryService,
        RequestRateLimitService requestRateLimitService,
        RequestUsageMeasurer requestUsageMeasurer,
        AudiobookLibraryService audiobookLibraryService,
        @Value("${chatbot.provider:mock}") String chatbotProvider,
        @Value("${spring.ai.google.genai.chat.options.model:}") String chatModelName
    ) {
        this.ttsWorkbenchService = ttsWorkbenchService;
        this.currentUserResolver = currentUserResolver;
        this.promptHistoryService = promptHistoryService;
        this.requestRateLimitService = requestRateLimitService;
        this.requestUsageMeasurer = requestUsageMeasurer;
        this.audiobookLibraryService = audiobookLibraryService;
        this.analysisProviderModelName = providerModelName(chatbotProvider, chatModelName);
    }

    @PostMapping("/speaker-voice-analysis")
    public SpeakerVoiceAnalysisResponse analyzeSpeakers(@RequestBody SpeakerVoiceAnalysisRequest request, Authentication authentication) {
        log.debug("/speaker-voice-analysis will send request "+request.toString());
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            SpeakerVoiceAnalysisResponse response = ttsWorkbenchService.analyze(request.rawDialogue());
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return response;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/speaker-split-analysis")
    public SpeakerSplitAnalysisResponse splitDialogue(@RequestBody SpeakerSplitAnalysisRequest request, Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        enforceLimit(user, ModelType.TEXT_MODEL, request.rawDialogue(), analysisProviderModelName);
        try {
            SpeakerSplitAnalysisResponse response = ttsWorkbenchService.split(request.rawDialogue(), request.speakers());
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.SUCCESS);
            return response;
        } catch (RuntimeException ex) {
            promptHistoryService.record(user, ModelType.TEXT_MODEL, analysisProviderModelName, request.rawDialogue(), PromptRequestStatus.FAILED);
            throw ex;
        }
    }

    @PostMapping("/emotion-annotation-analysis")
    public EmotionAnnotationAnalysisResponse annotateEmotions(@RequestBody EmotionAnnotationAnalysisRequest request) {
        return ttsWorkbenchService.annotate(request.turns());
    }

    @PostMapping("/final-request-preview")
    public FinalTtsRequestPreviewResponse previewFinalRequest(@RequestBody FinalTtsRequestPreviewRequest request) {
        return ttsWorkbenchService.buildFinalRequest(request);
    }

    @PostMapping("/single-speaker-render-plan")
    public SingleSpeakerRenderPlanResponse previewSingleSpeakerRenderPlan(
        @RequestBody SingleSpeakerRenderPlanRequest request
    ) {
        return ttsWorkbenchService.planSingleSpeakerRenderRequests(request);
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
            // Extract unique speakers and segment count from render requests
            Set<String> uniqueSpeakers = new HashSet<>();
            int segmentCount = 0;
            String firstSpeakerName = null;
            String firstVoiceName = null;

            if (requestPlan.renderRequests() != null && !requestPlan.renderRequests().isEmpty()) {
                // Extract metadata from first render request for persistence
                var firstRequest = requestPlan.renderRequests().get(0);
                var firstSpeakerConfig = extractSpeakerVoiceConfig(firstRequest.voice());
                firstSpeakerName = firstSpeakerConfig.speakerAlias();
                firstVoiceName = firstSpeakerConfig.speakerId();

                for (var request : requestPlan.renderRequests()) {
                    // Count render requests as segments
                    segmentCount++;

                    // Extract speaker name from voice configuration
                    var speakerConfig = extractSpeakerVoiceConfig(request.voice());
                    String speaker = speakerConfig.speakerAlias();
                    if (speaker != null && !speaker.isBlank()) {
                        uniqueSpeakers.add(speaker);
                    }
                }
            }

            int speakerCount = uniqueSpeakers.size();
            Integer estimatedDuration = DurationEstimator.estimateSpeakingDurationSeconds(promptText);

            TtsAudioFile audioFile = ttsWorkbenchService.createAudio(requestPlan);

            com.example.ttslab.audiobooks.AudiobookProject project;
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
                .header("X-Audiobook-Project-Id", project.id())
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

    private SpeakerVoiceConfig extractSpeakerVoiceConfig(java.util.Map<String, Object> voice) {
        if (voice == null) {
            return new SpeakerVoiceConfig("", "");
        }

        try {
            Object multiSpeakerVoiceConfigObj = voice.get("multiSpeakerVoiceConfig");
            if (!(multiSpeakerVoiceConfigObj instanceof java.util.Map)) {
                return new SpeakerVoiceConfig("", "");
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> multiSpeakerVoiceConfig = (java.util.Map<String, Object>) multiSpeakerVoiceConfigObj;
            Object speakerVoiceConfigsObj = multiSpeakerVoiceConfig.get("speakerVoiceConfigs");

            if (!(speakerVoiceConfigsObj instanceof java.util.List)) {
                return new SpeakerVoiceConfig("", "");
            }

            @SuppressWarnings("unchecked")
            java.util.List<Object> speakerVoiceConfigs = (java.util.List<Object>) speakerVoiceConfigsObj;
            if (speakerVoiceConfigs.isEmpty()) {
                return new SpeakerVoiceConfig("", "");
            }

            Object firstConfigObj = speakerVoiceConfigs.get(0);
            if (!(firstConfigObj instanceof java.util.Map)) {
                return new SpeakerVoiceConfig("", "");
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> firstConfig = (java.util.Map<String, Object>) firstConfigObj;
            String speakerAlias = stringValue(firstConfig, "speakerAlias");
            String speakerId = stringValue(firstConfig, "speakerId");

            return new SpeakerVoiceConfig(speakerAlias, speakerId);
        } catch (Exception ex) {
            log.debug("Failed to extract speaker voice config, using empty defaults", ex);
            return new SpeakerVoiceConfig("", "");
        }
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

    private record SpeakerVoiceConfig(String speakerAlias, String speakerId) {}
}
