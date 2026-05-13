package com.example.ttslab.audiobooks.workflow;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.GlobalApiExceptionHandler;
import com.example.ttslab.prompts.CurrentUserResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=user-1",
    "MOCK_USER_EMAIL=user1@example.com",
    "MOCK_USER_NAME=Test User One",
    "MOCK_USER_ROLES=USER",
    "spring.ai.model.chat=google-genai",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalApiExceptionHandler.class)
class ScriptPreviewWorkflowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AudiobookProjectRepository audiobookProjectRepository;

    @Autowired
    private AudiobookSpeechSegmentRepository audiobookSpeechSegmentRepository;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @SpyBean
    private AudiobookWorkflowStateService audiobookWorkflowStateService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "Test User One", List.of("USER"), "mock");
    private String projectId;
    private String segmentId;

    @BeforeEach
    void setUp() {
        when(currentUserResolver.resolve(any())).thenReturn(user);

        projectId = UUID.randomUUID().toString();
        segmentId = UUID.randomUUID().toString();

        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "The Amber Signal",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        AudiobookProject savedProject = audiobookProjectRepository.save(project);

        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            segmentId,
            savedProject,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.APPROVED,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Narrator",
            null,
            null,
            null,
            "The opening line.",
            "[calm] The opening line.",
            null
        );
        segment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        audiobookSpeechSegmentRepository.save(segment);
    }

    @Test
    void scriptPreviewSaveRollsBackPreviewRowsWhenStageUpdateFails() throws Exception {
        doThrow(new RuntimeException("stage transition failed"))
            .when(audiobookWorkflowStateService)
            .markScriptReview(any());

        MvcResult result = mockMvc.perform(post("/api/audiobooks/workflow/script-preview-save")
                .contentType("application/json")
                .content("""
                    {
                      "projectId": "%s",
                      "turns": [
                        {"speaker": "Narrator", "text": "The revised opening line."}
                      ]
                    }
                    """.formatted(projectId)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected server error occurred. Please try again later."))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());

        AudiobookProject project = audiobookProjectRepository.findById(projectId).orElseThrow();
        assertThat(project.getWorkflowStage()).isEqualTo(AudiobookWorkflowStage.CAST_APPROVED);

        AudiobookSpeechSegment segment = audiobookSpeechSegmentRepository.findById(segmentId).orElseThrow();
        assertThat(segment.getSpeakerName()).isEqualTo("Narrator");
        assertThat(segment.getOriginalText()).isEqualTo("The opening line.");
        assertThat(segment.getStyledText()).isEqualTo("[calm] The opening line.");
        assertThat(segment.getReviewStatus()).isEqualTo(AudiobookSpeechSegmentReviewStatus.APPROVED);
    }
}
