package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static com.example.ttslab.contract.TestContracts.readBytes;
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
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.repository.AudioAssetRepository;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import java.time.Instant;
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
    private AudiobookSpeechSegmentRepository audiobookSpeechSegmentRepository;

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @Autowired
    private SpeakerCharacterRepository speakerCharacterRepository;

    @Autowired
    private AudiobookLibraryService audiobookLibraryService;

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
                .content(readText("audiobook-workflow/create-audio/generated-preview/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tts-render-request-1.mp3\""))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/generated-preview/response.body.bin")))
            .andReturn();

        String projectId = result.getResponse().getHeader("X-Audiobook-Project-Id");
        assertThat(projectId).isNotBlank();

        AudiobookProject project = audiobookProjectRepository.findByIdAndUserId(projectId, user.id()).orElseThrow();
        assertThat(project.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.PERFORMANCE_READY);
        assertThat(project.isAudioAssetsCurrent()).isFalse();

        List<AudioAsset> assets = audioAssetRepository.findByProjectId(projectId);
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().getStatus()).isEqualTo(AudioAssetStatus.READY);
        assertThat(assets.getFirst().getFilename()).isEqualTo("tts-render-request-1.mp3");
        assertThat(assets.getFirst().getContentType()).isEqualTo("audio/mpeg");

        List<AudiobookSpeechSegment> segments = audiobookSpeechSegmentRepository.findByProjectId(projectId);
        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().getSegmentOrigin()).isEqualTo(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        assertThat(segments.getFirst().getSpeakerName()).isEqualTo("Narrator");
        assertThat(segments.getFirst().getOriginalText()).isEqualTo("Hello");
        assertThat(assets.getFirst().getSpeechSegmentId()).isEqualTo(segments.getFirst().getId());
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(projectId, AudiobookSpeechSegmentOrigin.GENERATED_AUDIO))
            .isEmpty();
        assertThat(speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc(projectId))
            .hasSize(1)
            .extracting(SpeakerCharacter::getSpeakerName)
            .containsExactly("Narrator");

        MvcResult snapshotResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audiobooks/workflow/projects/{projectId}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.speakers").isArray())
            .andExpect(jsonPath("$.speakers[0].speakerName").value("Narrator"))
            .andReturn();
        assertInteractionMatchesContract(snapshotResult.getRequest(), snapshotResult.getResponse());
        assertThat(snapshotResult.getResponse().getContentAsString()).contains("\"speakers\"");

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioUsesExistingAudioGeneratedProjectWhenProjectIdIsProvided() throws Exception {
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
        existingProject.setWorkflowStage(AudiobookWorkflowStage.AUDIO_GENERATED);
        existingProject.setAudioAssetsCurrent(true);
        audiobookProjectRepository.save(existingProject);

        AudiobookSpeechSegment existingSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            "Kore",
            null,
            "Hello",
            null,
            null
        );
        existingSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        existingSegment = audiobookSpeechSegmentRepository.save(existingSegment);

        MvcResult firstResult = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/generated-preview/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().exists("X-Audiobook-Project-Id"))
            .andExpect(header().string("X-Audiobook-Project-Id", existingProject.getId()))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tts-render-request-1.mp3\""))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/generated-preview/response.body.bin")))
            .andReturn();
        assertInteractionMatchesContract(firstResult.getRequest(), firstResult.getResponse());

        AudiobookProject project = audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow();
        assertThat(project.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);
        assertThat(project.isAudioAssetsCurrent()).isFalse();

        List<AudioAsset> assets = audioAssetRepository.findByProjectId(existingProject.getId());
        assertThat(assets).hasSize(1);
        String firstAssetId = assets.getFirst().getId();
        assertThat(assets.getFirst().getStatus()).isEqualTo(AudioAssetStatus.READY);
        assertThat(assets.getFirst().getFilename()).isEqualTo("tts-render-request-1.mp3");
        assertThat(assets.getFirst().getSpeechSegmentId()).isEqualTo(existingSegment.getId());
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(existingProject.getId(), AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW))
            .hasSize(1);
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(existingProject.getId(), AudiobookSpeechSegmentOrigin.GENERATED_AUDIO))
            .isEmpty();

        MvcResult secondResult = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/generated-preview/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().string("X-Audiobook-Project-Id", existingProject.getId()))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/generated-preview/response.body.bin")))
            .andReturn();
        assertInteractionMatchesContract(secondResult.getRequest(), secondResult.getResponse());

        AudiobookProject projectAfterSecondCall = audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow();
        assertThat(projectAfterSecondCall.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);
        assertThat(projectAfterSecondCall.isAudioAssetsCurrent()).isFalse();
        List<AudioAsset> assetsAfterSecondCall = audioAssetRepository.findByProjectId(existingProject.getId());
        assertThat(assetsAfterSecondCall).hasSize(1);
        assertThat(assetsAfterSecondCall.getFirst().getId()).isEqualTo(firstAssetId);

        byte[] body = firstResult.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(new byte[] {'I', 'D', '3'});

        byte[] secondBody = secondResult.getResponse().getContentAsByteArray();
        assertThat(secondBody).startsWith(new byte[] {'I', 'D', '3'});
    }

    @Test
    void createAudioPersistsEachRequestAgainstItsMatchingPreviewSegment() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Multi Part Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.AUDIO_GENERATED);
        existingProject.setAudioAssetsCurrent(true);
        audiobookProjectRepository.save(existingProject);

        String firstPartText = "First part.";
        String secondPartText = String.join(" ", List.of(
            "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen"
        ));

        AudiobookSpeechSegment firstSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            "Kore",
            null,
            firstPartText,
            null,
            null
        );
        firstSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        firstSegment = audiobookSpeechSegmentRepository.save(firstSegment);

        AudiobookSpeechSegment secondSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            1,
            "Speech segment 2",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Mara",
            null,
            "Iapetus",
            null,
            secondPartText,
            null,
            null
        );
        secondSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        secondSegment = audiobookSpeechSegmentRepository.save(secondSegment);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/generated-plan/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().string("X-Audiobook-Project-Id", existingProject.getId()))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tts-render-plan.mp3\""))
            .andExpect(content().bytes(readBytes("audiobook-workflow/create-audio/generated-plan/response.body.bin")))
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).hasSize(2);
        assertThat(audioAssetRepository.findBySegmentIdOrderByCreatedAtDesc(firstSegment.getId())).hasSize(1);
        assertThat(audioAssetRepository.findBySegmentIdOrderByCreatedAtDesc(firstSegment.getId()).getFirst().getDurationSeconds()).isEqualTo(1);
        assertThat(audioAssetRepository.findBySegmentIdOrderByCreatedAtDesc(secondSegment.getId())).hasSize(1);
        assertThat(audioAssetRepository.findBySegmentIdOrderByCreatedAtDesc(secondSegment.getId()).getFirst().getDurationSeconds()).isEqualTo(6);
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(existingProject.getId(), AudiobookSpeechSegmentOrigin.GENERATED_AUDIO))
            .isEmpty();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioRejectsStalePreviewSpeakerMismatch() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Speaker Mismatch Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.PERFORMANCE_READY);
        existingProject.setAudioAssetsCurrent(false);
        audiobookProjectRepository.save(existingProject);

        AudiobookSpeechSegment firstSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            "Kore",
            null,
            "First part.",
            null,
            null
        );
        firstSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        audiobookSpeechSegmentRepository.save(firstSegment);

        AudiobookSpeechSegment secondSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            1,
            "Speech segment 2",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Mara",
            null,
            "Iapetus",
            null,
            "Second part.",
            null,
            null
        );
        secondSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        audiobookSpeechSegmentRepository.save(secondSegment);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/stale-speaker-mismatch/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SCRIPT_PREVIEW_SEGMENT_MISMATCH"))
            .andExpect(jsonPath("$.message").value("The script preview no longer matches the saved script turns."))
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioRejectsPreviewCountMismatch() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Mismatch Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.PERFORMANCE_READY);
        existingProject.setAudioAssetsCurrent(false);
        audiobookProjectRepository.save(existingProject);

        AudiobookSpeechSegment onlySegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            "Kore",
            null,
            "First part.",
            null,
            null
        );
        onlySegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        audiobookSpeechSegmentRepository.save(onlySegment);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/generated-plan/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SCRIPT_PREVIEW_SEGMENT_MISMATCH"))
            .andExpect(jsonPath("$.message").value("The script preview no longer matches the saved script turns."))
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(existingProject.getId(), AudiobookSpeechSegmentOrigin.GENERATED_AUDIO))
            .isEmpty();
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
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
                .content(readText("audiobook-workflow/create-audio/generated-preview/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_WORKFLOW_PERFORMANCE_NOT_READY"))
            .andExpect(jsonPath("$.message").value("Emotion and pacing must be saved before audio can be generated."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertThat(audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow().getWorkflowStage())
            .isEqualTo(AudiobookWorkflowStage.CAST_APPROVED);
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioRejectsExistingProjectWithoutPreviewSegments() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Missing Preview Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.PERFORMANCE_READY);
        existingProject.setAudioAssetsCurrent(false);
        audiobookProjectRepository.save(existingProject);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", existingProject.getId())
                .contentType("application/json")
                .content(readText("audiobook-workflow/create-audio/generated-preview/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("SCRIPT_PREVIEW_SEGMENTS_REQUIRED"))
            .andExpect(jsonPath("$.message").value("Script preview segments must be saved before audio can be generated."))
            .andReturn();

        assertThat(audioAssetRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertThat(audiobookSpeechSegmentRepository.findByProjectId(existingProject.getId())).isEmpty();
        assertThat(audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow().getWorkflowStage())
            .isEqualTo(AudiobookWorkflowStage.PERFORMANCE_READY);
        assertThat(result.getResponse().getContentAsString()).contains("SCRIPT_PREVIEW_SEGMENTS_REQUIRED");
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void finalizeAudioGenerationMarksTheProjectCurrentAfterAllPartsAreSaved() throws Exception {
        AudiobookProject existingProject = audiobookProjectRepository.save(new AudiobookProject(
            UUID.randomUUID().toString(),
            user.id(),
            "Finalize Audiobook",
            com.example.ttslab.audiobooks.model.AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        ));
        existingProject.setWorkflowStage(AudiobookWorkflowStage.PERFORMANCE_READY);
        existingProject.setAudioAssetsCurrent(false);
        audiobookProjectRepository.save(existingProject);

        AudiobookSpeechSegment firstSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            0,
            "Speech segment 1",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            "Kore",
            null,
            "First part.",
            null,
            null
        );
        firstSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        firstSegment = audiobookSpeechSegmentRepository.save(firstSegment);

        AudiobookSpeechSegment secondSegment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            existingProject,
            1,
            "Speech segment 2",
            com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Mara",
            null,
            "Iapetus",
            null,
            "Second part.",
            null,
            null
        );
        secondSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        secondSegment = audiobookSpeechSegmentRepository.save(secondSegment);

        audiobookLibraryService.persistAudioAsset(
            existingProject,
            new com.example.ttslab.audiobooks.workflow.TtsAudioFile(new byte[] {'I', 'D', '3'}, "audio/mpeg", "part-1.mp3"),
            new com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderRequest(
                java.util.Map.of("text", "First part.", "segmentOrderIndex", 0),
                java.util.Map.of("speakerName", "Narrator", "name", "Kore"),
                java.util.Map.of()
            ),
            1,
            1,
            1,
            5
        );
        audiobookLibraryService.persistAudioAsset(
            existingProject,
            new com.example.ttslab.audiobooks.workflow.TtsAudioFile(new byte[] {'I', 'D', '3'}, "audio/mpeg", "part-2.mp3"),
            new com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderRequest(
                java.util.Map.of("text", "Second part.", "segmentOrderIndex", 1),
                java.util.Map.of("speakerName", "Mara", "name", "Iapetus"),
                java.util.Map.of()
            ),
            1,
            1,
            1,
            5
        );

        AudiobookProject beforeFinalize = audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow();
        assertThat(beforeFinalize.isAudioAssetsCurrent()).isFalse();
        assertThat(audiobookSpeechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(existingProject.getId(), AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW))
            .extracting(AudiobookSpeechSegment::getOriginalText)
            .containsExactly("First part.", "Second part.");

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/projects/{projectId}/audio-generated", existingProject.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(existingProject.getId()))
            .andExpect(jsonPath("$.workflowStage").value("AUDIO_GENERATED"))
            .andExpect(jsonPath("$.audioAssetsCurrent").value(true))
            .andReturn();
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject afterFinalize = audiobookProjectRepository.findByIdAndUserId(existingProject.getId(), user.id()).orElseThrow();
        assertThat(afterFinalize.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);
        assertThat(afterFinalize.isAudioAssetsCurrent()).isTrue();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("tts-lab-create-audio-integration-");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create storage root for create-audio integration test.", ex);
        }
    }
}
