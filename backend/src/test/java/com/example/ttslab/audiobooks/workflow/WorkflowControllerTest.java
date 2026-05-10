package com.example.ttslab.audiobooks.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller tests for Workflow API endpoints.
 * Tests request/response handling and error scenarios.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(WorkflowController.class)
@DisplayName("WorkflowController Tests")
class WorkflowControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private CurrentUserResolver currentUserResolver;
  @MockBean private WorkflowService workflowService;
  @MockBean private WorkflowSessionRepository sessionRepository;

  private CurrentUser testUser;

  @BeforeEach
  void setup() {
    testUser = new CurrentUser("user-123", "user@example.com", "Test User", List.of("USER"), "mock");
    when(currentUserResolver.resolve(any())).thenReturn(testUser);
  }

  @Test
  @DisplayName("POST /workflows creates new workflow session")
  void createWorkflowReturnsCreatedWithSessionId() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.DRAFT,
            "Alice said hello",
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(workflowService.initiateWorkflow(eq(testUser), any())).thenReturn(session);

    mockMvc
        .perform(
            post("/api/audiobooks/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storyText\":\"Alice said hello\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("session-1"))
        .andExpect(jsonPath("$.state").value("DRAFT"))
        .andExpect(jsonPath("$.projectId").value("project-1"));
  }

  @Test
  @DisplayName("GET /workflows/{sessionId} returns current workflow state")
  void getWorkflowReturnsCurrentState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.SPEAKERS_DISCOVERED,
            "Story text",
            "{\"speakers\":[]}",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(java.util.Optional.of(session));

    mockMvc
        .perform(get("/api/audiobooks/workflows/session-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("session-1"))
        .andExpect(jsonPath("$.state").value("SPEAKERS_DISCOVERED"))
        .andExpect(jsonPath("$.userId").value(testUser.id()));
  }

  @Test
  @DisplayName("POST /workflows/{id}/discover-speakers transitions to SPEAKERS_DISCOVERED")
  void discoverSpeakersTransitionsState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.SPEAKERS_DISCOVERED,
            "Story",
            "{\"speakers\":[{\"name\":\"Alice\",\"role\":\"Protagonist\"}]}",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(workflowService.executeDiscoverSpeakers("session-1", testUser))
        .thenReturn(session);

    mockMvc
        .perform(post("/api/audiobooks/workflows/session-1/discover-speakers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SPEAKERS_DISCOVERED"))
        .andExpect(jsonPath("$.availableActions").isArray());
  }

  @Test
  @DisplayName("POST /workflows/{id}/split-dialogue transitions to DIALOGUE_SPLIT")
  void splitDialogueTransitionsState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.DIALOGUE_SPLIT,
            "Story",
            "{\"speakers\":[]}",
            "{\"turns\":[]}",
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(workflowService.executeSplitDialogue(eq("session-1"), eq(testUser), any()))
        .thenReturn(session);

    mockMvc
        .perform(
            post("/api/audiobooks/workflows/session-1/split-dialogue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"speakers\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DIALOGUE_SPLIT"));
  }

  @Test
  @DisplayName("POST /workflows/{id}/annotate-dialogue transitions to DIALOGUE_ANNOTATED")
  void annotateDialogueTransitionsState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.DIALOGUE_ANNOTATED,
            "Story",
            "{\"speakers\":[]}",
            "{\"turns\":[]}",
            "{\"turns\":[{\"text\":\"[calm] Hello\"}]}",
            null,
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(workflowService.executeAnnotateDialogue(eq("session-1"), eq(testUser), any()))
        .thenReturn(session);

    mockMvc
        .perform(
            post("/api/audiobooks/workflows/session-1/annotate-dialogue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"turns\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("DIALOGUE_ANNOTATED"));
  }

  @Test
  @DisplayName("POST /workflows/{id}/configure-output transitions to CONFIGURED")
  void configureOutputTransitionsState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.CONFIGURED,
            "Story",
            "{}",
            "{}",
            "{}",
            "{\"request\":{}}",
            null,
            Instant.now(),
            Instant.now(),
            null);

    when(workflowService.executeConfigureOutput(
            eq("session-1"), eq(testUser), any(), any(), any(), any()))
        .thenReturn(session);

    mockMvc
        .perform(
            post("/api/audiobooks/workflows/session-1/configure-output")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"languageCode\":\"en-US\",\"modelName\":\"gpt-4\",\"audioEncoding\":\"mp3\",\"voiceAssignments\":{}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CONFIGURED"));
  }

  @Test
  @DisplayName("POST /workflows/{id}/generate-audio transitions to AUDIO_GENERATED")
  void generateAudioTransitionsState() throws Exception {
    WorkflowSession session =
        new WorkflowSession(
            "session-1",
            "project-1",
            testUser.id(),
            WorkflowState.AUDIO_GENERATED,
            "Story",
            "{}",
            "{}",
            "{}",
            "{}",
            "{}",
            Instant.now(),
            Instant.now(),
            Instant.now());

    when(workflowService.executeGenerateAudio("session-1", testUser)).thenReturn(session);

    mockMvc
        .perform(post("/api/audiobooks/workflows/session-1/generate-audio"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("AUDIO_GENERATED"))
        .andExpect(jsonPath("$.completedAt").exists());
  }

  @Test
  @DisplayName("GET /workflows/{id} returns 404 when session not found")
  void getWorkflowReturns404WhenNotFound() throws Exception {
    when(sessionRepository.findSessionForUser("missing-session", testUser.id()))
        .thenReturn(java.util.Optional.empty());

    mockMvc
        .perform(get("/api/audiobooks/workflows/missing-session"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Discover speakers returns error when workflow API fails")
  void discoverSpeakersReturnsConflictOnInvalidState() throws Exception {
    when(workflowService.executeDiscoverSpeakers("session-1", testUser))
        .thenThrow(
            new IllegalStateException(
                "Cannot transition from current state to ANALYZING_SPEAKERS"));

    mockMvc
        .perform(post("/api/audiobooks/workflows/session-1/discover-speakers"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Split dialogue returns error on invalid speakers")
  void splitDialogueHandlesServiceError() throws Exception {
    when(workflowService.executeSplitDialogue(eq("session-1"), eq(testUser), any()))
        .thenThrow(new RuntimeException("Speaker analysis failed"));

    mockMvc
        .perform(
            post("/api/audiobooks/workflows/session-1/split-dialogue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"speakers\":[]}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Multiple state transitions follow workflow sequence")
  void workflowFollowsLinearStateSequence() throws Exception {
    // Create session in DRAFT
    WorkflowSession draftSession =
        new WorkflowSession(
            "seq-1", "proj-1", testUser.id(), WorkflowState.DRAFT, "Text", null, null, null, null,
            null, Instant.now(), Instant.now(), null);

    // Transition to ANALYZING_SPEAKERS
    WorkflowSession analyzingSession =
        draftSession.withState(WorkflowState.ANALYZING_SPEAKERS);

    // Transition to SPEAKERS_DISCOVERED
    WorkflowSession discoveredSession =
        analyzingSession.withState(WorkflowState.SPEAKERS_DISCOVERED);

    when(workflowService.initiateWorkflow(eq(testUser), any())).thenReturn(draftSession);
    when(workflowService.executeDiscoverSpeakers("seq-1", testUser))
        .thenReturn(discoveredSession);

    // Verify DRAFT state
    mockMvc
        .perform(
            post("/api/audiobooks/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storyText\":\"Story\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.state").value("DRAFT"));

    // Verify transition to SPEAKERS_DISCOVERED
    when(sessionRepository.findSessionForUser("seq-1", testUser.id()))
        .thenReturn(java.util.Optional.of(discoveredSession));

    mockMvc
        .perform(post("/api/audiobooks/workflows/seq-1/discover-speakers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SPEAKERS_DISCOVERED"));
  }
}
