package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static com.example.ttslab.contract.OpenApiContractAssertions.assertResponseMatchesContract;
import static com.example.ttslab.contract.TestContracts.readBytes;
import static com.example.ttslab.contract.TestContracts.readText;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.error.GlobalApiExceptionHandler;
import com.example.ttslab.audiobooks.workflow.*;
import com.example.ttslab.audiobooks.workflow.service.AudiobookWorkflowService;
import com.example.ttslab.audiobooks.workflow.service.EmotionAnnotationPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.SpeakerSplitPersistenceService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import java.util.List;
import com.example.ttslab.error.ApiException;
import java.util.Map;
import org.mockito.InOrder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AudiobookWorkflowController.class)
@Import(GlobalApiExceptionHandler.class)
class AudiobookWorkflowControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AudiobookWorkflowService audiobookWorkflowService;

    @MockBean
    private SpeakerVoiceAnalysisService speakerVoiceAnalysisService;

    @MockBean
    private SpeakerSplitPersistenceService speakerSplitPersistenceService;

    @MockBean
    private EmotionAnnotationPersistenceService emotionAnnotationPersistenceService;

    @MockBean
    private AudiobookProjectCreationService audiobookProjectCreationService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private PromptHistoryService promptHistoryService;

    @MockBean
    private RequestRateLimitService requestRateLimitService;

    @MockBean
    private RequestUsageMeasurer requestUsageMeasurer;

    @MockBean
    private AudiobookLibraryService audiobookLibraryService;

    @MockBean
    private ChatbotProperties chatbotProperties;

    private AudiobookProject testProject;

    @org.junit.jupiter.api.BeforeEach
    void setupCurrentUser() {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser("u1", "u1@example.com", "User One", List.of("USER"), "mock"));
        when(chatbotProperties.provider()).thenReturn("mock");
        when(requestRateLimitService.unit()).thenReturn(RequestRateLimitUnit.WORDS);
        when(requestUsageMeasurer.measure(any(), eq(RequestRateLimitUnit.WORDS))).thenReturn(1L);
        when(requestRateLimitService.checkAndConsume(any(), eq(ModelType.SPEECH_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.SPEECH_MODEL, true, 1, 600, 599, 1, 0, 1, RequestRateLimitUnit.WORDS));
        when(requestRateLimitService.checkAndConsume(any(), eq(ModelType.TEXT_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.TEXT_MODEL, true, 1, 600, 599, 1, 0, 1, RequestRateLimitUnit.WORDS));

        // Mock audiobook library service
        testProject = new AudiobookProject(
            "test-project-1",
            "u1",
            "Test Audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            null,
            null,
            null,
            null
        );
        when(audiobookLibraryService.createProjectForGeneration(any()))
            .thenReturn(testProject);
        when(audiobookLibraryService.getProjectForUser(anyString(), any(CurrentUser.class)))
            .thenReturn(testProject);

        AudioAsset testAsset = new AudioAsset(
            "test-asset-1",
            "test-project-1",
            "test-scene-1",
            AudioAssetType.PREVIEW_MP3,
            1,
            "test-key",
            "tts-render-request-1.mp3",
            "audio/mpeg",
            3L,
            null,
            AudioAssetStatus.READY,
            null
        );
        when(audiobookLibraryService.persistAudioAsset(any(AudiobookProject.class), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(Integer.class)))
            .thenReturn(testAsset);
    }

    @Test
    void speakerVoiceAnalysisReturnsSuggestedVoices() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello")).thenReturn(new SpeakerVoiceAnalysisResponse(List.of(
            new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", SpeakerVoice.ACHIRD)
        ), null, "The Hidden Signal"));
        when(audiobookProjectCreationService.createProject("u1", "The Hidden Signal")).thenReturn(testProject);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/speaker-voice-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/speaker-voice-analysis/default/response.json")))
            .andReturn();

        InOrder inOrder = org.mockito.Mockito.inOrder(speakerVoiceAnalysisService, audiobookProjectCreationService);
        inOrder.verify(speakerVoiceAnalysisService).analyze("Alice: Hello");
        inOrder.verify(audiobookProjectCreationService).createProject("u1", "The Hidden Signal");
        verify(speakerVoiceAnalysisService).syncProjectCharacters("test-project-1", List.of(new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", SpeakerVoice.ACHIRD)));
        verify(promptHistoryService).record(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq("mock"), eq("Alice: Hello"), eq(com.example.ttslab.prompts.PromptRequestStatus.SUCCESS));
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void speakerSplitAnalysisReturnsTurns() throws Exception {
        when(speakerSplitPersistenceService.splitAndPersist(eq(testProject), eq("A: Hello"), anyList())).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("A", "Hello")
        )));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-split-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/speaker-split-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/speaker-split-analysis/default/response.json")))
            .andReturn();

        verify(audiobookLibraryService).getProjectForUser(eq("test-project-1"), any(CurrentUser.class));
        verify(speakerSplitPersistenceService).splitAndPersist(eq(testProject), eq("A: Hello"), anyList());
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void emotionAnnotationAnalysisReturnsAnnotatedTurns() throws Exception {
        when(emotionAnnotationPersistenceService.loadScriptPreviewTurns(testProject)).thenReturn(List.of(
            new SpeakerSplitTurn("A", "Hello!")
        ));
        when(audiobookWorkflowService.annotate(any())).thenReturn(new EmotionAnnotationAnalysisResponse(List.of(
            new AnnotatedSpeakerTurn("A", "[urgent] Hello!")
        )));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/emotion-annotation-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/emotion-annotation-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/emotion-annotation-analysis/default/response.json")))
            .andReturn();

        verify(audiobookLibraryService).getProjectForUser(eq("test-project-1"), any(CurrentUser.class));
        verify(emotionAnnotationPersistenceService).loadScriptPreviewTurns(eq(testProject));
        verify(audiobookWorkflowService).annotate(List.of(new SpeakerSplitTurn("A", "Hello!")));
        verify(emotionAnnotationPersistenceService).persistStyledText(eq(testProject), anyList());
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void emotionAnnotationAnalysisRejectsMissingProjectId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/emotion-annotation-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/emotion-annotation-analysis/validation-failed/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("The request is invalid. Please check your input and try again."))
            .andReturn();

        assertResponseMatchesContract("/api/audiobooks/workflow/emotion-annotation-analysis", com.atlassian.oai.validator.model.Request.Method.POST, result.getResponse());
    }

    @Test
    void saveScriptPreviewPersistsEditedTurns() throws Exception {
        when(emotionAnnotationPersistenceService.saveScriptPreviewTurns(eq(testProject), anyList())).thenReturn(List.of(
            new SpeakerSplitTurn("Narrator", "The opening line."),
            new SpeakerSplitTurn("Mara", "We go now.")
        ));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/script-preview-save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/script-preview-save/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/script-preview-save/default/response.json")))
            .andReturn();

        verify(audiobookLibraryService).getProjectForUser(eq("test-project-1"), any(CurrentUser.class));
        verify(emotionAnnotationPersistenceService).saveScriptPreviewTurns(eq(testProject), anyList());
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void finalRequestPreviewReturnsJsonShape() throws Exception {
        when(audiobookWorkflowService.buildFinalRequest(any(FinalTtsRequestPreviewRequest.class))).thenReturn(new FinalTtsRequestPreviewResponse(
            Map.of("prompt", "Prompt", "multiSpeakerMarkup", Map.of("turns", List.of())),
            Map.of("languageCode", "en-US", "modelName", "{{google-model}}", "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of())),
            Map.of("audioEncoding", "MP3")
        ));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/final-request-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/final-request-preview/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/final-request-preview/default/response.json")))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void singleSpeakerRenderPlanReturnsRenderRequests() throws Exception {
        when(audiobookWorkflowService.planSingleSpeakerRenderRequests(any(SingleSpeakerRenderPlanRequest.class)))
            .thenReturn(new SingleSpeakerRenderPlanResponse(List.of(
                new SingleSpeakerRenderRequest(
                    Map.of("text", "Hello"),
                    Map.of("languageCode", "en-US", "name", "Kore", "modelName", "{{google-model}}"),
                    Map.of("audioEncoding", "MP3")
                )
            )));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/single-speaker-render-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/single-speaker-render-plan/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/single-speaker-render-plan/default/response.json")))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioReturnsDownloadableMp3() throws Exception {
        when(audiobookWorkflowService.createAudio(any(SingleSpeakerRenderPlanResponse.class)))
            .thenReturn(new TtsAudioFile(new byte[] {'I', 'D', '3'}, "audio/mpeg", "tts-render-request-1.mp3"));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/create-audio/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/default/response.body.bin")))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("X-Audiobook-Project-Id", "test-project-1"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void speakerVoiceAnalysisRateLimitedReturns429WithRetryAfter() throws Exception {
        when(requestRateLimitService.checkAndConsume(any(), eq(ModelType.TEXT_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.TEXT_MODEL, false, 600, 600, 0, 1, 42, 1, RequestRateLimitUnit.WORDS));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "42"))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void apiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello")).thenThrow(new ApiException(
            HttpStatus.BAD_GATEWAY,
            "AUDIOBOOK_WORKFLOW_PROVIDER_FAILED",
            "The speaker voice analysis provider is currently unavailable. Please try again later.",
            null,
            new RuntimeException("provider timeout")
        ));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .header("X-Request-Id", "test-request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_WORKFLOW_PROVIDER_FAILED"))
            .andExpect(jsonPath("$.message").value("The speaker voice analysis provider is currently unavailable. Please try again later."))
            .andExpect(jsonPath("$.requestId").value("test-request-1"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void unexpectedExceptionReturnsSafeStructuredErrorResponse() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello")).thenThrow(new IllegalStateException("database-password=secret"));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected server error occurred. Please try again later."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

}


