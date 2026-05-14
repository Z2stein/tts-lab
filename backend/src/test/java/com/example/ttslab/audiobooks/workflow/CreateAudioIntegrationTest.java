package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.TestContracts.readText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudioAssetRepository;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=user-1",
    "MOCK_USER_EMAIL=user1@example.com",
    "MOCK_USER_NAME=Test User One",
    "MOCK_USER_ROLES=USER",
    "spring.ai.model.chat=google-genai",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:create-audio-integration-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = false)
class CreateAudioIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudiobookProjectRepository audiobookProjectRepository;

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private RequestRateLimitService requestRateLimitService;

    @MockBean
    private RequestUsageMeasurer requestUsageMeasurer;

    @MockBean
    private PromptHistoryService promptHistoryService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "Test User One", List.of("USER"), "mock");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("audiobooks.storage.root", () -> STORAGE_ROOT.toString());
    }

    @BeforeEach
    void setUp() {
        when(currentUserResolver.resolve(any())).thenReturn(user);
        when(requestRateLimitService.unit()).thenReturn(RequestRateLimitUnit.WORDS);
        when(requestUsageMeasurer.measure(anyString(), eq(RequestRateLimitUnit.WORDS))).thenReturn(1L);
        when(requestRateLimitService.checkAndConsume(any(), eq(ModelType.SPEECH_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.SPEECH_MODEL, true, 1, 600, 599, 1, 0, 1, RequestRateLimitUnit.WORDS));
    }

    @Test
    void createAudioPersistsGeneratedPreviewAndReturnsMp3() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tts-render-request-1.mp3\""))
            .andReturn();

        String projectId = result.getResponse().getHeader("X-Audiobook-Project-Id");
        assertThat(projectId).isNotBlank();

        AudiobookProject project = audiobookProjectRepository.findByIdAndUserId(projectId, user.id()).orElseThrow();
        assertThat(project.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);

        List<AudioAsset> assets = audioAssetRepository.findByProjectId(projectId);
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().getStatus()).isEqualTo(AudioAssetStatus.READY);
        assertThat(assets.getFirst().getFilename()).isEqualTo("tts-render-request-1.mp3");
        assertThat(assets.getFirst().getContentType()).isEqualTo("audio/mpeg");

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(new byte[] {'I', 'D', '3'});
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("TTS-LAB-MOCK-MP3", "Hello");
    }

    @Test
    void createAudioUsesExistingProjectWhenProjectIdIsProvided() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Existing Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.PERFORMANCE_READY);
        audiobookProjectRepository.save(existingProject);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("X-Audiobook-Project-Id", existingProject.getId()))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tts-render-request-1.mp3\""))
            .andReturn();

        AudiobookProject project = audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow();
        assertThat(project.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);

        List<AudioAsset> assets = audioAssetRepository.findByProjectId(existingProject.getId());
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().getStatus()).isEqualTo(AudioAssetStatus.READY);
        assertThat(assets.getFirst().getFilename()).isEqualTo("tts-render-request-1.mp3");

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(new byte[] {'I', 'D', '3'});
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("TTS-LAB-MOCK-MP3", "Hello");
    }

    @Test
    void createAudioRejectsExistingProjectWhenPerformanceIsNotReady() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Blocked Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        audiobookProjectRepository.save(existingProject);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/default/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_WORKFLOW_PERFORMANCE_NOT_READY"))
            .andExpect(jsonPath("$.message").value("Emotion and pacing must be saved before audio can be generated."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertThat(audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow().getWorkflowStage())
            .isEqualTo(AudiobookWorkflowStage.CAST_APPROVED);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("tts-lab-create-audio-integration-");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create storage root for create-audio integration test.", ex);
        }
    }
}
