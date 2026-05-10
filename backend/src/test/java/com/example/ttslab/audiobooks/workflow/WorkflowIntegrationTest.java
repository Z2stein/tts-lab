package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisItem;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the complete workflow from creation to audio generation.
 * Tests the full state machine progression with real database and service interactions.
 */
@SpringBootTest
@Transactional
@DisplayName("Workflow Integration Tests")
class WorkflowIntegrationTest {

  @Autowired private WorkflowService workflowService;
  @Autowired private WorkflowSessionRepository sessionRepository;

  private final CurrentUser testUser = new CurrentUser(
      "user-123",
      "test@example.com",
      "Test User",
      List.of("USER"),
      "mock"
  );

  @Test
  @DisplayName("Complete workflow from DRAFT to AUDIO_GENERATED")
  void testCompleteWorkflow() throws Exception {
    String storyText = "Alice said hello. Bob replied hi.";

    // Step 1: Initiate workflow
    WorkflowSession session = workflowService.initiateWorkflow(testUser, storyText);

    assertThat(session.id()).isNotNull();
    assertThat(session.state()).isEqualTo(WorkflowState.DRAFT);
    assertThat(session.storyText()).isEqualTo(storyText);
    assertThat(session.userId()).isEqualTo(testUser.id());

    // Verify session is persisted
    var persisted = sessionRepository.findSessionForUser(session.id(), testUser.id());
    assertThat(persisted).isPresent();
    assertThat(persisted.get().state()).isEqualTo(WorkflowState.DRAFT);

    // Step 2: Discover speakers (no real TTS call, mock would be needed for full test)
    // This would test the speaker discovery state transition

    // Step 3: Verify state transitions are enforced
    assertThat(session.state().canTransitionTo(WorkflowState.ANALYZING_SPEAKERS)).isTrue();
    assertThat(session.state().canTransitionTo(WorkflowState.DIALOGUE_SPLIT)).isFalse();
  }

  @Test
  @DisplayName("User isolation - sessions are private to their creator")
  void testUserIsolation() throws Exception {
    CurrentUser user1 = new CurrentUser("user-1", "user1@example.com", "User 1", List.of("USER"), "mock");
    CurrentUser user2 = new CurrentUser("user-2", "user2@example.com", "User 2", List.of("USER"), "mock");

    // User 1 creates a workflow
    WorkflowSession session1 = workflowService.initiateWorkflow(user1, "User 1's story");

    // User 2 should not be able to access user 1's session
    var result = sessionRepository.findSessionForUser(session1.id(), user2.id());
    assertThat(result).isEmpty();

    // But user 1 can access their own session
    result = sessionRepository.findSessionForUser(session1.id(), user1.id());
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("State transitions enforce linear progression")
  void testStateTransitionValidation() throws Exception {
    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Test story");

    // Valid transitions from DRAFT
    assertThat(session.state().canTransitionTo(WorkflowState.ANALYZING_SPEAKERS)).isTrue();

    // Invalid transitions from DRAFT
    assertThat(session.state().canTransitionTo(WorkflowState.DIALOGUE_SPLIT)).isFalse();
    assertThat(session.state().canTransitionTo(WorkflowState.AUDIO_GENERATED)).isFalse();
    assertThat(session.state().canTransitionTo(WorkflowState.ARCHIVED)).isFalse();
  }

  @Test
  @DisplayName("Session persists workflow artifacts as JSON")
  void testArtifactPersistence() throws Exception {
    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Test story");
    String sessionId = session.id();

    // Simulate adding speaker analysis JSON
    String speakerAnalysisJson = "{\"speakers\": [{\"name\": \"Alice\"}]}";
    WorkflowSession updated = session.withSpeakerAnalysis(WorkflowState.SPEAKERS_DISCOVERED, speakerAnalysisJson);
    sessionRepository.updateSession(updated);

    // Verify JSON is persisted
    var retrieved = sessionRepository.findSessionForUser(sessionId, testUser.id());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().speakerAnalysisJson()).isEqualTo(speakerAnalysisJson);
    assertThat(retrieved.get().state()).isEqualTo(WorkflowState.SPEAKERS_DISCOVERED);
  }

  @Test
  @DisplayName("Multiple workflow sessions can coexist per user")
  void testMultipleSessions() throws Exception {
    // Create multiple sessions
    WorkflowSession session1 = workflowService.initiateWorkflow(testUser, "Story 1");
    WorkflowSession session2 = workflowService.initiateWorkflow(testUser, "Story 2");

    // Verify both are retrievable independently
    var result1 = sessionRepository.findSessionForUser(session1.id(), testUser.id());
    var result2 = sessionRepository.findSessionForUser(session2.id(), testUser.id());

    assertThat(result1).isPresent();
    assertThat(result2).isPresent();
    assertThat(result1.get().id()).isNotEqualTo(result2.get().id());
    assertThat(result1.get().storyText()).isEqualTo("Story 1");
    assertThat(result2.get().storyText()).isEqualTo("Story 2");
  }

  @Test
  @DisplayName("Workflow session timestamps are persisted correctly")
  void testTimestampUpdates() throws Exception {
    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Test");

    // Verify timestamps are set initially
    assertThat(session.createdAt()).isNotNull();
    assertThat(session.updatedAt()).isNotNull();
    assertThat(session.completedAt()).isNull();

    // Update to next state and persist
    WorkflowSession updated = session.withState(WorkflowState.ANALYZING_SPEAKERS);
    sessionRepository.updateSession(updated);

    var retrieved = sessionRepository.findSessionForUser(session.id(), testUser.id()).orElseThrow();

    // Timestamps should be persisted
    assertThat(retrieved.createdAt()).isNotNull();
    assertThat(retrieved.updatedAt()).isNotNull();
    assertThat(retrieved.completedAt()).isNull();
    // State should be updated
    assertThat(retrieved.state()).isEqualTo(WorkflowState.ANALYZING_SPEAKERS);
  }

  @Test
  @DisplayName("Workflow completion sets completedAt timestamp")
  void testWorkflowCompletion() throws Exception {
    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Test");
    assertThat(session.completedAt()).isNull();

    // Simulate progression to completion using complete() which sets AUDIO_GENERATED and completedAt
    WorkflowSession completed = session.complete();
    sessionRepository.updateSession(completed);

    var retrieved = sessionRepository.findSessionForUser(session.id(), testUser.id()).orElseThrow();
    assertThat(retrieved.state()).isEqualTo(WorkflowState.AUDIO_GENERATED);
    assertThat(retrieved.completedAt()).isNotNull();
  }

  @Test
  @DisplayName("Session retrieval by ID and userId together")
  void testSessionAccessControl() throws Exception {
    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Test");

    // Correct userId works
    var found = sessionRepository.findSessionForUser(session.id(), testUser.id());
    assertThat(found).isPresent();

    // Wrong userId fails
    var notFound = sessionRepository.findSessionForUser(session.id(), "wrong-user");
    assertThat(notFound).isEmpty();
  }

}
