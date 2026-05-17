package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static com.example.ttslab.contract.TestContracts.readText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudioAssetRepository;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.GlobalApiExceptionHandler;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=user-1",
    "MOCK_USER_EMAIL=user1@example.com",
    "MOCK_USER_NAME=Test User One",
    "MOCK_USER_ROLES=USER",
    "spring.ai.model.chat=google-genai",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration",
    "spring.datasource.url=jdbc:h2:mem:audiobook-workflow-integration-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(GlobalApiExceptionHandler.class)
class AudiobookWorkflowIntegrationTest {
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired MockMvc mockMvc;
    @Autowired AudiobookProjectRepository audiobookProjectRepository;
    @Autowired AudiobookSpeechSegmentRepository audiobookSpeechSegmentRepository;
    @Autowired SpeakerCharacterRepository speakerCharacterRepository;
    @Autowired AudioAssetRepository audioAssetRepository;
    @Autowired AudiobookLibraryService audiobookLibraryService;

    @MockBean CurrentUserResolver currentUserResolver;
    @MockBean RequestRateLimitService requestRateLimitService;
    @MockBean RequestUsageMeasurer requestUsageMeasurer;
    @MockBean PromptHistoryService promptHistoryService;

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
        when(requestRateLimitService.checkAndConsume(any(), any(), eq(1L)))
            .thenReturn(new RequestRateLimitResult(ModelType.TEXT_MODEL, true, 1, 1800, 1799, 1, 0, 1, RequestRateLimitUnit.WORDS));
    }

    @Test
    void getSnapshotReturnsCastReviewProject() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_REVIEW);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/workflow/projects/{projectId}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.workflowStage").value("CAST_REVIEW"))
            .andExpect(jsonPath("$.speakers").isArray())
            .andExpect(jsonPath("$.speakers.length()").value(2))
            .andExpect(jsonPath("$.speakers[0].speakerName").value("Mara"))
            .andExpect(jsonPath("$.speakers[1].speakerName").value("Jonas"))
            .andExpect(jsonPath("$.scriptTurns").isEmpty())
            .andExpect(jsonPath("$.annotatedTurns").isEmpty())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void getSnapshotReturnsCastApprovedProject() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/workflow/projects/{projectId}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("CAST_APPROVED"))
            .andExpect(jsonPath("$.speakers.length()").value(2))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void getSnapshotReturnsPerformanceReadyProject() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.PERFORMANCE_READY);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        AudiobookSpeechSegment seg1 = buildSegment(UUID.randomUUID().toString(), project, 0,
            "The rain hit the windows.\nThe café was nearly empty.",
            "[quiet] The rain hit the windows.\nThe café was nearly empty.", mara);
        AudiobookSpeechSegment seg2 = buildSegment(UUID.randomUUID().toString(), project, 1,
            "So this is your surprise?", "[curious] So this is your surprise?", jonas);
        AudiobookSpeechSegment seg3 = buildSegment(UUID.randomUUID().toString(), project, 2,
            "I thought you would be pleased.", "[softly] I thought you would be pleased.", mara);
        audiobookSpeechSegmentRepository.saveAll(List.of(seg1, seg2, seg3));

        MvcResult result = mockMvc.perform(get("/api/audiobooks/workflow/projects/{projectId}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("PERFORMANCE_READY"))
            .andExpect(jsonPath("$.scriptTurns.length()").value(3))
            .andExpect(jsonPath("$.annotatedTurns.length()").value(3))
            .andExpect(jsonPath("$.scriptTurns[0].speaker").value("Mara"))
            .andExpect(jsonPath("$.annotatedTurns[0].text").value("[quiet] The rain hit the windows.\nThe café was nearly empty."))
            .andExpect(jsonPath("$.productionSettings.modelName").value("gemini-3.1-flash-tts-preview"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void getSnapshotReturnsAudioGeneratedProject() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.AUDIO_GENERATED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        speakerCharacterRepository.save(mara);

        AudiobookSpeechSegment seg1 = buildSegment(UUID.randomUUID().toString(), project, 0, "First line.", "[calm] First line.", mara);
        audiobookSpeechSegmentRepository.save(seg1);

        audiobookLibraryService.persistAudioAsset(project,
            new TtsAudioFile(new byte[]{'I', 'D', '3'}, "audio/mpeg", "part-1.mp3"),
            new SingleSpeakerRenderRequest(
                Map.of("text", "First line.", "segmentOrderIndex", 0),
                Map.of("speakerName", "Mara", "name", "Kore"),
                Map.of()
            ), 1, 5);

        project = audiobookProjectRepository.findById(projectId).orElseThrow();
        project.setAudioAssetsCurrent(true);
        audiobookProjectRepository.save(project);

        MvcResult result = mockMvc.perform(get("/api/audiobooks/workflow/projects/{projectId}", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("AUDIO_GENERATED"))
            .andExpect(jsonPath("$.audioAssetsCurrent").value(true))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void analyzeSpeakersCreatesProjectAndReturnsSpeakers() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-voice-analysis")
                .contentType("application/json")
                .content(readText("audiobook-workflow/speaker-voice-analysis/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speakers").isArray())
            .andExpect(jsonPath("$.speakers[0].speakerName").value("Alice"))
            .andExpect(jsonPath("$.projectId").isNotEmpty())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        String responseJson = result.getResponse().getContentAsString();
        String projectId = responseJson.split("\"projectId\":\"")[1].split("\"")[0];
        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.CAST_REVIEW);
        List<SpeakerCharacter> characters = speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        assertThat(characters).hasSize(1);
        assertThat(characters.getFirst().getSpeakerName()).isEqualTo("Alice");
    }

    @Test
    void approveCastAdvancesWorkflowStage() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_REVIEW);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/projects/{projectId}/cast-approval", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("CAST_APPROVED"))
            .andExpect(jsonPath("$.speakers.length()").value(2))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.CAST_APPROVED);
    }

    @Test
    void castUpdatePersistsCharactersAndResetsStageToCastReview() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        speakerCharacterRepository.save(mara);

        MvcResult result = mockMvc.perform(patch("/api/audiobooks/workflow/projects/{projectId}/cast", projectId)
                .contentType("application/json")
                .content("""
                    {
                      "speakers": [
                        {"speakerName": "Captain Mara", "roleDescription": "Storm pilot", "voiceSuggestion": "PUCK"}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("CAST_REVIEW"))
            .andExpect(jsonPath("$.speakers.length()").value(1))
            .andExpect(jsonPath("$.speakers[0].speakerName").value("Captain Mara"))
            .andExpect(jsonPath("$.speakers[0].roleDescription").value("Storm pilot"))
            .andExpect(jsonPath("$.speakers[0].voiceSuggestion").value("PUCK"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.CAST_REVIEW);
        List<SpeakerCharacter> characters = speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        assertThat(characters).hasSize(1);
        assertThat(characters.getFirst().getSpeakerName()).isEqualTo("Captain Mara");
        assertThat(characters.getFirst().getRoleDescription()).isEqualTo("Storm pilot");
        assertThat(characters.getFirst().getVoiceSuggestion()).isEqualTo(SpeakerVoice.PUCK);
    }

    @Test
    void castUpdateIsRejectedAfterScriptPreviewExists() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.SCRIPT_REVIEW);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        speakerCharacterRepository.save(mara);

        MvcResult result = mockMvc.perform(patch("/api/audiobooks/workflow/projects/{projectId}/cast", projectId)
                .contentType("application/json")
                .content("""
                    {
                      "speakers": [
                        {"speakerName": "Captain Mara", "roleDescription": "Storm pilot", "voiceSuggestion": "PUCK"}
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUDIOBOOK_WORKFLOW_CAST_EDIT_NOT_ALLOWED"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void splitDialoguePersistsScriptTurns() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        String requestBody = """
            {
              "rawDialogue": "Mara: We go now.\\nJonas: Together.",
              "speakers": [
                {"speakerName": "Mara", "roleDescription": "Bold traveler", "voiceSuggestion": "KORE"},
                {"speakerName": "Jonas", "roleDescription": "Careful friend", "voiceSuggestion": "IAPETUS"}
              ],
              "projectId": "%s"
            }
            """.formatted(projectId);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/speaker-split-analysis")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turns").isArray())
            .andExpect(jsonPath("$.turns.length()").value(2))
            .andExpect(jsonPath("$.turns[0].speaker").value("Mara"))
            .andExpect(jsonPath("$.turns[1].speaker").value("Jonas"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        List<AudiobookSpeechSegment> segments = audiobookSpeechSegmentRepository
            .findByProjectIdAndSegmentOriginOrderByOrderIndex(projectId, AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getOriginalText()).isEqualTo("We go now.");
        assertThat(segments.get(1).getOriginalText()).isEqualTo("Together.");
        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.SCRIPT_REVIEW);
    }

    @Test
    void approveScriptAdvancesWorkflowStage() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.SCRIPT_REVIEW);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        AudiobookSpeechSegment seg1 = buildSegment(UUID.randomUUID().toString(), project, 0, "First.", "[calm] First.", mara);
        AudiobookSpeechSegment seg2 = buildSegment(UUID.randomUUID().toString(), project, 1, "Second.", "[calm] Second.", jonas);
        audiobookSpeechSegmentRepository.saveAll(List.of(seg1, seg2));

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/projects/{projectId}/script-approval", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("SCRIPT_APPROVED"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.SCRIPT_APPROVED);
    }

    @Test
    void annotateEmotionsPersistsStyledText() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.SCRIPT_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter narrator = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Narrator", "Story voice", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.save(narrator);

        AudiobookSpeechSegment seg = buildSegment(UUID.randomUUID().toString(), project, 0, "The lamps dimmed.", null, narrator);
        seg = audiobookSpeechSegmentRepository.save(seg);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/emotion-annotation-analysis")
                .contentType("application/json")
                .content("{\"projectId\": \"%s\"}".formatted(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("PERFORMANCE_READY"))
            .andExpect(jsonPath("$.annotatedTurns.length()").value(1))
            .andExpect(jsonPath("$.annotatedTurns[0].speaker").value("Narrator"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        List<AudiobookSpeechSegment> segments = audiobookSpeechSegmentRepository
            .findByProjectIdAndSegmentOriginOrderByOrderIndex(projectId, AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        assertThat(segments).hasSize(1);
        assertThat(segments.getFirst().getStyledText()).isEqualTo("[calm] The lamps dimmed.");
        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.PERFORMANCE_READY);
    }

    @Test
    void saveScriptPreviewPersistsTurns() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Mara", "Bold traveler", SpeakerVoice.KORE);
        SpeakerCharacter jonas = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Jonas", "Careful friend", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(mara, jonas));

        AudiobookSpeechSegment seg1 = buildSegment(UUID.randomUUID().toString(), project, 0, "Old text 1", null, mara);
        AudiobookSpeechSegment seg2 = buildSegment(UUID.randomUUID().toString(), project, 1, "Old text 2", null, jonas);
        audiobookSpeechSegmentRepository.saveAll(List.of(seg1, seg2));

        String requestBody = readText("audiobook-workflow/script-preview-save/default/request.json")
            .replace("project-1", projectId);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/script-preview-save")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turns.length()").value(2))
            .andExpect(jsonPath("$.turns[0].speaker").value("Mara"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        List<AudiobookSpeechSegment> segments = audiobookSpeechSegmentRepository
            .findByProjectIdAndSegmentOriginOrderByOrderIndex(projectId, AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        assertThat(segments).hasSize(2);
        assertThat(segments.getFirst().getCharacter().getSpeakerName()).isEqualTo("Mara");
        assertThat(segments.getFirst().getStyledText()).isNull();
    }

    @Test
    void updateProductionSettingsPersistsSettings() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.CAST_APPROVED);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter character = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Narrator", "Story voice", SpeakerVoice.KORE);
        speakerCharacterRepository.save(character);

        String requestBody = """
            {
              "prompt": "A thrilling audiobook.",
              "languageCode": "en-GB",
              "modelName": "gemini-2.0-flash-tts-preview",
              "audioEncoding": "MP3"
            }
            """;

        MvcResult result = mockMvc.perform(patch("/api/audiobooks/workflow/projects/{projectId}/production-settings", projectId)
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.productionSettings.languageCode").value("en-GB"))
            .andExpect(jsonPath("$.productionSettings.modelName").value("gemini-2.0-flash-tts-preview"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject saved = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(saved.getProductionLanguageCode()).isEqualTo("en-GB");
        assertThat(saved.getProductionModelName()).isEqualTo("gemini-2.0-flash-tts-preview");
        assertThat(saved.getProductionPrompt()).isEqualTo("A thrilling audiobook.");
    }

    @Test
    void previewFinalRequestReturnsTtsRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/final-request-preview")
                .contentType("application/json")
                .content(readText("audiobook-workflow/final-request-preview/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.input").exists())
            .andExpect(jsonPath("$.voice").exists())
            .andExpect(jsonPath("$.audioConfig").exists())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void previewSingleSpeakerRenderPlanReturnsRequests() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/single-speaker-render-plan")
                .contentType("application/json")
                .content(readText("audiobook-workflow/single-speaker-render-plan/default/request.json")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.renderRequests").isArray())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void createAudioReturnsMp3() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.PERFORMANCE_READY);
        project.setProductionLanguageCode("en-US");
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter narrator = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Narrator", "Story voice", SpeakerVoice.KORE);
        speakerCharacterRepository.save(narrator);

        AudiobookSpeechSegment seg = buildSegment(UUID.randomUUID().toString(), project, 0, "Hello", "[calm] Hello", narrator);
        audiobookSpeechSegmentRepository.save(seg);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/create-audio")
                .param("projectId", projectId)
                .param("targetSegmentIndex", "0"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("audio/mpeg"))
            .andExpect(header().string("X-Audiobook-Project-Id", projectId))
            .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).startsWith(new byte[]{'I', 'D', '3'});

        List<com.example.ttslab.audiobooks.model.AudioAsset> assets = audioAssetRepository.findByProjectId(projectId);
        assertThat(assets).hasSize(1);
        assertThat(assets.getFirst().getStatus()).isEqualTo(AudioAssetStatus.READY);
    }

    @Test
    void finalizeAudioGenerationMarksProjectCurrent() throws Exception {
        String projectId = UUID.randomUUID().toString();
        AudiobookProject project = buildProject(projectId, AudiobookWorkflowStage.PERFORMANCE_READY);
        project.setAudioAssetsCurrent(false);
        project = audiobookProjectRepository.save(project);

        SpeakerCharacter narrator = buildCharacter(UUID.randomUUID().toString(), projectId, 0, "Narrator", "Story voice", SpeakerVoice.KORE);
        SpeakerCharacter mara = buildCharacter(UUID.randomUUID().toString(), projectId, 1, "Mara", "Character", SpeakerVoice.IAPETUS);
        speakerCharacterRepository.saveAll(List.of(narrator, mara));

        AudiobookSpeechSegment seg1 = buildSegment(UUID.randomUUID().toString(), project, 0, "First part.", "[calm] First part.", narrator);
        AudiobookSpeechSegment seg2 = buildSegment(UUID.randomUUID().toString(), project, 1, "Second part.", "[calm] Second part.", mara);
        seg1 = audiobookSpeechSegmentRepository.save(seg1);
        seg2 = audiobookSpeechSegmentRepository.save(seg2);

        audiobookLibraryService.persistAudioAsset(project,
            new TtsAudioFile(new byte[]{'I', 'D', '3'}, "audio/mpeg", "part-1.mp3"),
            new SingleSpeakerRenderRequest(
                Map.of("text", "First part.", "segmentOrderIndex", 0),
                Map.of("speakerName", "Narrator", "name", "Kore"),
                Map.of()
            ), 1, 5);

        audiobookLibraryService.persistAudioAsset(project,
            new TtsAudioFile(new byte[]{'I', 'D', '3'}, "audio/mpeg", "part-2.mp3"),
            new SingleSpeakerRenderRequest(
                Map.of("text", "Second part.", "segmentOrderIndex", 1),
                Map.of("speakerName", "Mara", "name", "Iapetus"),
                Map.of()
            ), 1, 5);

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/projects/{projectId}/audio-generated", projectId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStage").value("AUDIO_GENERATED"))
            .andExpect(jsonPath("$.audioAssetsCurrent").value(true))
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject saved = audiobookProjectRepository.findByIdAndUserId(projectId, user.id()).orElseThrow();
        assertThat(saved.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.AUDIO_GENERATED);
        assertThat(saved.isAudioAssetsCurrent()).isTrue();
    }

    // Private helper methods

    private AudiobookProject buildProject(String projectId, AudiobookWorkflowStage stage) {
        AudiobookProject project = new AudiobookProject(
            projectId, user.id(), "The Hidden Signal",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0, null, null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setWorkflowStage(stage);
        project.setStoryText("Mara: We go now.\nJonas: Together.");
        project.setProductionPrompt("An immersive audiobook performance with a clear narrator and distinct character voices.");
        project.setProductionLanguageCode("en-US");
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project.setProductionAudioEncoding("MP3");
        project.setAudioAssetsCurrent(false);
        return project;
    }

    private SpeakerCharacter buildCharacter(String id, String projectId, int sortOrder,
                                            String name, String role, SpeakerVoice voice) {
        return new SpeakerCharacter(id, projectId, sortOrder, name, role, voice,
            Instant.parse("2026-05-12T10:00:00Z"));
    }

    private AudiobookSpeechSegment buildSegment(String id, AudiobookProject project,
            int orderIndex, String originalText, String styledText, SpeakerCharacter character) {
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            id, project, orderIndex,
            "Speech segment " + (orderIndex + 1),
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            originalText, styledText, character
        );
        segment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        return segment;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("tts-lab-audiobook-workflow-integration-");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create storage root.", ex);
        }
    }
}
