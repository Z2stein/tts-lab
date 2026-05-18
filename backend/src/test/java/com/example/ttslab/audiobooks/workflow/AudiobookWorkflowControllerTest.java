package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static com.example.ttslab.contract.OpenApiContractAssertions.assertResponseMatchesContract;
import static com.example.ttslab.contract.TestContracts.readBytes;
import static com.example.ttslab.contract.TestContracts.readJson;
import static com.example.ttslab.contract.TestContracts.readText;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.error.GlobalApiExceptionHandler;
import com.example.ttslab.audiobooks.workflow.*;
import com.example.ttslab.audiobooks.workflow.service.AudiobookWorkflowService;
import com.example.ttslab.audiobooks.workflow.service.EmotionAnnotationPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.RenderPlanPersistenceService;
import com.example.ttslab.audiobooks.workflow.service.ScriptPreviewWorkflowService;
import com.example.ttslab.audiobooks.workflow.service.SpeakerSplitPersistenceService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStateService;
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
import java.time.Instant;
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
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private ScriptPreviewWorkflowService scriptPreviewWorkflowService;

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
    private AudiobookWorkflowStateService audiobookWorkflowStateService;

    @MockBean
    private StoryDraftService storyDraftService;

    @MockBean
    private com.example.ttslab.audiobooks.workflow.service.RenderPlanPersistenceService renderPlanPersistenceService;

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
            .thenReturn(new RequestRateLimitResult(ModelType.TEXT_MODEL, true, 1, 1800, 1799, 1, 0, 1, RequestRateLimitUnit.WORDS));

        // Mock audiobook library service
        testProject = new AudiobookProject(
            "project-1",
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

        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            testProject.getId(),
            0,
            "Narrator",
            null,
            SpeakerVoice.KORE,
            Instant.now()
        );
        AudiobookSpeechSegment testSegment = new AudiobookSpeechSegment(
            "test-scene-1",
            testProject,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.now(),
            Instant.now(),
            "Hello",
            null,
            character
        );
        testSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        when(audiobookLibraryService.preparePreviewSegments(any(AudiobookProject.class), anyList(), anyBoolean()))
            .thenReturn(List.of(testSegment));

        AudioAsset testAsset = new AudioAsset(
            "test-asset-1",
            "project-1",
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
        when(audiobookLibraryService.persistAudioAsset(any(AudiobookProject.class), any(TtsAudioFile.class), any(AudiobookSpeechSegment.class),  any(Integer.class), any(Integer.class)))
            .thenReturn(testAsset);
    }

    @Test
    void speakerVoiceAnalysisReturnsSuggestedVoices() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello", null)).thenReturn(new SpeakerVoiceAnalysisResponse(List.of(
            new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", SpeakerVoice.ACHIRD)
        ), null, "The Hidden Signal", "de-DE", "de-DE"));
        when(audiobookProjectCreationService.createProjectWithSpeakers("u1", "The Hidden Signal", "Alice: Hello", "de-DE", "de-DE",
            List.of(new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", SpeakerVoice.ACHIRD)))).thenReturn(testProject);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/speaker-voice-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/speaker-voice-analysis/default/response.json")))
            .andReturn();

        InOrder inOrder = org.mockito.Mockito.inOrder(speakerVoiceAnalysisService, audiobookProjectCreationService);
        inOrder.verify(speakerVoiceAnalysisService).analyze("Alice: Hello", null);
        inOrder.verify(audiobookProjectCreationService).createProjectWithSpeakers("u1", "The Hidden Signal", "Alice: Hello", "de-DE", "de-DE",
            List.of(new SpeakerVoiceAnalysisItem("Alice", "Detected dialogue speaker", SpeakerVoice.ACHIRD)));
        verify(promptHistoryService).record(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq("mock"), eq("Alice: Hello"), eq(com.example.ttslab.prompts.PromptRequestStatus.SUCCESS));
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void speakerSplitAnalysisReturnsTurns() throws Exception {
        testProject.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        when(speakerSplitPersistenceService.splitAndPersist(eq(testProject), eq("A: Hello"), anyList(), isNull())).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("A", "Hello")
        )));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-split-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/speaker-split-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/speaker-split-analysis/default/response.json")))
            .andReturn();

        verify(audiobookLibraryService).getProjectForUser(eq("project-1"), any(CurrentUser.class));
        verify(speakerSplitPersistenceService).splitAndPersist(eq(testProject), eq("A: Hello"), anyList(), isNull());
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void castUpdateReturnsWorkflowSnapshot() throws Exception {
        when(audiobookWorkflowStateService.updateCast(any(CurrentUser.class), eq("project-1"), anyList()))
            .thenReturn(readWorkflowSnapshot("audiobook-workflow/workflow-snapshot/cast-review/response.json"));

        MvcResult result = mockMvc.perform(patch("/api/audiobooks/workflow/projects/project-1/cast")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "speakers": [
                        {"speakerName": "Mara", "roleDescription": "Lead", "voiceSuggestion": "KORE"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("project-1"))
            .andExpect(jsonPath("$.workflowStage").value("CAST_REVIEW"))
            .andReturn();

        verify(audiobookWorkflowStateService).updateCast(any(CurrentUser.class), eq("project-1"), eq(List.of(
            new SpeakerVoiceAnalysisItem("Mara", "Lead", SpeakerVoice.KORE)
        )));
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void castUpdateRejectsWhenScriptAlreadyExists() throws Exception {
        when(audiobookWorkflowStateService.updateCast(any(CurrentUser.class), eq("project-1"), anyList()))
            .thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "AUDIOBOOK_WORKFLOW_CAST_EDIT_NOT_ALLOWED",
                "The cast can only be edited before the script preview exists."
            ));

        MvcResult result = mockMvc.perform(patch("/api/audiobooks/workflow/projects/project-1/cast")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "speakers": [
                        {"speakerName": "Mara", "roleDescription": "Lead", "voiceSuggestion": "KORE"}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_WORKFLOW_CAST_EDIT_NOT_ALLOWED"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void emotionAnnotationAnalysisReturnsWorkflowSnapshot() throws Exception {
        testProject.setWorkflowStage(AudiobookWorkflowStage.SCRIPT_APPROVED);
        when(emotionAnnotationPersistenceService.loadScriptPreviewTurns(testProject)).thenReturn(List.of(
            new SpeakerSplitTurn("Narrator", "The lamps dimmed.")
        ));
        when(audiobookWorkflowService.annotate(any(), isNull())).thenReturn(new EmotionAnnotationAnalysisResponse(List.of(
            new AnnotatedSpeakerTurn("Narrator", "[quiet] The lamps dimmed.")
        )));
        when(audiobookWorkflowStateService.snapshot(any(CurrentUser.class), eq("project-1")))
            .thenReturn(readWorkflowSnapshot("audiobook-workflow/emotion-annotation-analysis/default/response.json"));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/emotion-annotation-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/emotion-annotation-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/emotion-annotation-analysis/default/response.json")))
            .andReturn();

        verify(audiobookLibraryService).getProjectForUser(eq("project-1"), any(CurrentUser.class));
        verify(emotionAnnotationPersistenceService).loadScriptPreviewTurns(eq(testProject));
        verify(audiobookWorkflowService).annotate(List.of(new SpeakerSplitTurn("Narrator", "The lamps dimmed.")), null);
        verify(emotionAnnotationPersistenceService).persistStyledText(eq(testProject), anyList());
        verify(audiobookWorkflowStateService).markPerformanceReady(eq(testProject));
        verify(audiobookWorkflowStateService).snapshot(any(CurrentUser.class), eq("project-1"));
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
        testProject.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        when(scriptPreviewWorkflowService.saveScriptPreview(any(), any(ScriptPreviewSaveRequest.class))).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("Mara", "The last train had already left, and the station clock was wrong."),
            new SpeakerSplitTurn("Jonas", "Together.")
        )));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/script-preview-save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("audiobook-workflow/script-preview-save/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/script-preview-save/default/response.json")))
            .andReturn();

        verify(scriptPreviewWorkflowService).saveScriptPreview(any(CurrentUser.class), any(ScriptPreviewSaveRequest.class));
        verify(emotionAnnotationPersistenceService, never()).saveScriptPreviewTurns(any(), anyList());
        verify(audiobookWorkflowStateService, never()).markScriptReview(any());
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void getProjectSnapshotReturnsWorkflowSnapshot() throws Exception {
        when(audiobookWorkflowStateService.snapshot(any(CurrentUser.class), eq("project-1")))
            .thenReturn(readWorkflowSnapshot("audiobook-workflow/workflow-snapshot/cast-review/response.json"));

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audiobooks/workflow/projects/project-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("project-1"))
            .andExpect(jsonPath("$.workflowStage").value("CAST_REVIEW"))
            .andExpect(jsonPath("$.audioAssetsCurrent").value(false))
            .andExpect(jsonPath("$.performanceNotesStale").value(false))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void approveCastAdvancesWorkflowStageAndReturnsSnapshot() throws Exception {
        when(audiobookWorkflowStateService.approveCast(any(CurrentUser.class), eq("project-1")))
            .thenReturn(readWorkflowSnapshot("audiobook-workflow/workflow-snapshot/cast-approved/response.json"));

        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/audiobooks/workflow/projects/project-1/cast-approval"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("CAST_APPROVED"))
            .andExpect(jsonPath("$.performanceNotesStale").value(false))
            .andReturn();

        verify(audiobookWorkflowStateService).approveCast(any(CurrentUser.class), eq("project-1"));
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
                    Map.of("text", "Hello", "segmentOrderIndex", 0),
                    Map.of("languageCode", "en-US", "speakerName", "Narrator", "name", "Kore", "modelName", "{{google-model}}"),
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
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Test Project",
            AudiobookProjectStatus.DRAFT,
            "text",
            1,
            1,
            60,
            now,
            now
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "char-1",
            "project-1",
            0,
            "Narrator",
            "Main narrator voice",
            SpeakerVoice.ZEPHYR,
            now
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            30,
            now,
            now,
            "Hello",
            "Hello",
            character
        );
        project.getSpeechSegments().add(segment);

        when(renderPlanPersistenceService.loadProjectFromDatabase("project-1"))
            .thenReturn(project);
        when(audiobookWorkflowService.createAudio(any(AudiobookProject.class), anyInt()))
            .thenReturn(new TtsAudioFile(new byte[] {'I', 'D', '3'}, "audio/mpeg", "tts-render-request-1.mp3"));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", "project-1")
                .param("targetSegmentIndex", "0"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/default/response.body.bin")))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("X-Audiobook-Project-Id", "project-1"))
            .andReturn();
    }

    @Test
    void finalizeAudioGenerationReturnsSnapshot() throws Exception {
        when(audiobookWorkflowStateService.finalizeAudioGeneration(any(CurrentUser.class), eq("project-1")))
            .thenReturn(readWorkflowSnapshot("audiobook-workflow/workflow-snapshot/audio-generated/response.json"));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/projects/project-1/audio-generated"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value("project-1"))
            .andExpect(jsonPath("$.workflowStage").value("AUDIO_GENERATED"))
            .andExpect(jsonPath("$.audioAssetsCurrent").value(true))
            .andReturn();

        verify(audiobookWorkflowStateService).finalizeAudioGeneration(any(CurrentUser.class), eq("project-1"));
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void speakerVoiceAnalysisRateLimitedReturns429WithRetryAfter() throws Exception {
        when(requestRateLimitService.checkAndConsume(any(), eq(ModelType.TEXT_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.TEXT_MODEL, false, 1800, 1800, 0, 1, 42, 1, RequestRateLimitUnit.WORDS));

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
        when(speakerVoiceAnalysisService.analyze("Alice: Hello", null)).thenThrow(new ApiException(
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
        when(speakerVoiceAnalysisService.analyze("Alice: Hello", null)).thenThrow(new IllegalStateException("database-password=secret"));

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

    @Test
    void voiceCatalogReturnsAllEnumValues() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/audiobooks/workflow/voices"))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("audiobook-workflow/voices/default/response.json")))
            .andExpect(jsonPath("$.length()").value(SpeakerVoice.values().length))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void voiceCatalogItemHasCorrectAssetUrlConvention() throws Exception {
        mockMvc.perform(get("/api/audiobooks/workflow/voices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("zephyr"))
            .andExpect(jsonPath("$[0].imageUrl").value("/assets/voices/zephyr/avatar.png"))
            .andExpect(jsonPath("$[0].demoMp3Url").value("/assets/voices/zephyr/demo.mp3"))
            .andExpect(jsonPath("$[0].providerVoiceName").value("Zephyr"))
            .andExpect(jsonPath("$[0].displayName").value("Zephyr"))
            .andExpect(jsonPath("$[0].description").isNotEmpty());
    }

    private static AudiobookWorkflowSnapshotResponse readWorkflowSnapshot(String relativePath) throws IOException {
        return readJson(relativePath, AudiobookWorkflowSnapshotResponse.class);
    }

}
