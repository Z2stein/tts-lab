package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.error.GlobalApiExceptionHandler;
import com.example.ttslab.projects.ttsworkbench.service.TtsWorkbenchService;
import com.example.ttslab.audiobooks.wf.AudiobookProjectCreationService;
import com.example.ttslab.audiobooks.wf.ProjectCreationRequest;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.audiobooks.wf.speakeranalysis.SpeakerVoiceAnalysisItem;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TtsWorkbenchController.class)
@Import(GlobalApiExceptionHandler.class)
class TtsWorkbenchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsWorkbenchService ttsWorkbenchService;

    @MockBean
    private SpeakerVoiceAnalysisService speakerVoiceAnalysisService;

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

    private AudiobookProject testProject;

    @org.junit.jupiter.api.BeforeEach
    void setupCurrentUser() {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser("u1", "u1@example.com", "User One", List.of("USER"), "mock"));
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
            "TTS_WORKBENCH",
            1,
            null,
            null,
            null,
            null
        );
        when(audiobookLibraryService.createProjectForGeneration(any()))
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
        ), null));
        when(audiobookProjectCreationService.createProject(any(ProjectCreationRequest.class))).thenReturn(testProject);

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"speakers":[{"speakerName":"Alice","roleDescription":"Detected dialogue speaker","voiceSuggestion":"ACHIRD"}],"projectId":"test-project-1"}
                """));

        verify(promptHistoryService).record(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq("mock"), eq("Alice: Hello"), eq(com.example.ttslab.prompts.PromptRequestStatus.SUCCESS));
    }

    @Test
    void speakerSplitAnalysisReturnsTurns() throws Exception {
        when(ttsWorkbenchService.split(eq("A: Hello"), any())).thenReturn(new SpeakerSplitAnalysisResponse(List.of(
            new SpeakerSplitTurn("A", "Hello")
        )));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-split-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"A: Hello\",\"speakers\":[]}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"turns":[{"speaker":"A","text":"Hello"}]}
                """));
    }

    @Test
    void emotionAnnotationAnalysisReturnsAnnotatedTurns() throws Exception {
        when(ttsWorkbenchService.annotate(any())).thenReturn(new EmotionAnnotationAnalysisResponse(List.of(
            new AnnotatedSpeakerTurn("A", "[urgent] Hello!")
        )));

        mockMvc.perform(post("/api/projects/tts-workbench/emotion-annotation-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"turns\":[{\"speaker\":\"A\",\"text\":\"Hello!\"}]}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"turns":[{"speaker":"A","text":"[urgent] Hello!"}]}
                """));
    }

    @Test
    void finalRequestPreviewReturnsJsonShape() throws Exception {
        when(ttsWorkbenchService.buildFinalRequest(any(FinalTtsRequestPreviewRequest.class))).thenReturn(new FinalTtsRequestPreviewResponse(
            Map.of("prompt", "Prompt", "multiSpeakerMarkup", Map.of("turns", List.of())),
            Map.of("languageCode", "en-US", "modelName", "{{google-model}}", "multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", List.of())),
            Map.of("audioEncoding", "MP3")
        ));

        mockMvc.perform(post("/api/projects/tts-workbench/final-request-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"Prompt\",\"speakers\":[],\"annotatedTurns\":[],\"languageCode\":\"en-US\",\"modelName\":\"{{google-model}}\",\"audioEncoding\":\"MP3\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"input":{"prompt":"Prompt","multiSpeakerMarkup":{"turns":[]}},"voice":{"languageCode":"en-US","modelName":"{{google-model}}","multiSpeakerVoiceConfig":{"speakerVoiceConfigs":[]}},"audioConfig":{"audioEncoding":"MP3"}}
                """));
    }

    @Test
    void singleSpeakerRenderPlanReturnsRenderRequests() throws Exception {
        when(ttsWorkbenchService.planSingleSpeakerRenderRequests(any(SingleSpeakerRenderPlanRequest.class)))
            .thenReturn(new SingleSpeakerRenderPlanResponse(List.of(
                new SingleSpeakerRenderRequest(
                    Map.of("text", "Hello"),
                    Map.of("languageCode", "en-US", "name", "Kore", "modelName", "{{google-model}}"),
                    Map.of("audioEncoding", "MP3")
                )
            )));

        mockMvc.perform(post("/api/projects/tts-workbench/single-speaker-render-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"input":{"prompt":"Prompt","multiSpeakerMarkup":{"turns":[]}},"voice":{},"audioConfig":{}}
                    """))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"renderRequests":[{"input":{"text":"Hello"},"voice":{"languageCode":"en-US","name":"Kore","modelName":"{{google-model}}"},"audioConfig":{"audioEncoding":"MP3"}}]}
                """));
    }

    @Test
    void createAudioReturnsDownloadableMp3() throws Exception {
        when(ttsWorkbenchService.createAudio(any(SingleSpeakerRenderPlanResponse.class)))
            .thenReturn(new TtsAudioFile(new byte[] {'I', 'D', '3'}, "audio/mpeg", "tts-render-request-1.mp3"));

        mockMvc.perform(post("/api/projects/tts-workbench/create-audio")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"renderRequests":[{"input":{"text":"Hello"},"voice":{"languageCode":"en-US","name":"Kore"},"audioConfig":{"audioEncoding":"MP3"}}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(content().bytes(new byte[] {'I', 'D', '3'}))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("X-Audiobook-Project-Id", "test-project-1"));
    }

    @Test
    void apiExceptionReturnsStructuredErrorResponse() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello")).thenThrow(new ApiException(
            HttpStatus.BAD_GATEWAY,
            "TTS_WORKBENCH_PROVIDER_FAILED",
            "The speaker voice analysis provider is currently unavailable. Please try again later.",
            null,
            new RuntimeException("provider timeout")
        ));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .header("X-Request-Id", "test-request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("TTS_WORKBENCH_PROVIDER_FAILED"))
            .andExpect(jsonPath("$.message").value("The speaker voice analysis provider is currently unavailable. Please try again later."))
            .andExpect(jsonPath("$.requestId").value("test-request-1"));
    }

    @Test
    void unexpectedExceptionReturnsSafeStructuredErrorResponse() throws Exception {
        when(speakerVoiceAnalysisService.analyze("Alice: Hello")).thenThrow(new IllegalStateException("database-password=secret"));

        mockMvc.perform(post("/api/projects/tts-workbench/speaker-voice-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawDialogue\":\"Alice: Hello\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected server error occurred. Please try again later."))
            .andExpect(jsonPath("$.requestId").exists());
    }

}
