package com.example.ttslab.audiobooks.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.AudiobookRepository;
import com.example.ttslab.projects.ttsworkbench.AnnotatedSpeakerTurn;
import com.example.ttslab.projects.ttsworkbench.EmotionAnnotationAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoice;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisItem;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitTurn;
import com.example.ttslab.projects.ttsworkbench.TtsWorkbenchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for WorkflowService.
 * Tests workflow state transitions, persistence, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService Tests")
class WorkflowServiceTest {

  @Mock private WorkflowSessionRepository sessionRepository;
  @Mock private AudiobookRepository audiobookRepository;
  @Mock private TtsWorkbenchService ttsService;
  @Mock private ObjectMapper objectMapper;

  private WorkflowService workflowService;
  private CurrentUser testUser;

  @BeforeEach
  void setup() {
    workflowService = new WorkflowService(
        sessionRepository,
        audiobookRepository,
        ttsService,
        objectMapper
    );
    testUser = new CurrentUser("user-123", "user@example.com", "Test User", List.of("USER"), "mock");
  }

  // ========== INITIATE WORKFLOW TESTS ==========

  @Test
  @DisplayName("Initiates workflow creates session in DRAFT state")
  void initiateWorkflowCreatesSession() {
    String storyText = "Once upon a time, Alice said hello.";
    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);

    workflowService.initiateWorkflow(testUser, storyText);

    verify(sessionRepository).createSession(captor.capture());
    WorkflowSession created = captor.getValue();

    assertThat(created.userId()).isEqualTo(testUser.id());
    assertThat(created.state()).isEqualTo(WorkflowState.DRAFT);
    assertThat(created.storyText()).isEqualTo(storyText);
    assertThat(created.createdAt()).isNotNull();
  }

  @Test
  @DisplayName("Initiates workflow returns session with ID")
  void initiateWorkflowReturnsSession() {
    when(sessionRepository.createSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession session = workflowService.initiateWorkflow(testUser, "Story text");

    assertThat(session).isNotNull();
    assertThat(session.id()).isNotBlank();
    assertThat(session.projectId()).isNotBlank();
  }

  // ========== DISCOVER SPEAKERS TESTS ==========

  @Test
  @DisplayName("Discover speakers transitions to SPEAKERS_DISCOVERED on success")
  void discoverSpeakersSuccessTransitions() throws JsonProcessingException {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);
    List<SpeakerVoiceAnalysisItem> speakers = List.of(
        new SpeakerVoiceAnalysisItem("Alice", "Protagonist", SpeakerVoice.ACHIRD),
        new SpeakerVoiceAnalysisItem("Bob", "Narrator", SpeakerVoice.ZEPHYR)
    );
    SpeakerVoiceAnalysisResponse analysis = new SpeakerVoiceAnalysisResponse(speakers);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));
    when(ttsService.analyze("Story text")).thenReturn(analysis);
    when(objectMapper.writeValueAsString(analysis)).thenReturn("{\"speakers\":[]}");
    when(sessionRepository.updateSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession result = workflowService.executeDiscoverSpeakers("session-1", testUser);

    assertThat(result.state()).isEqualTo(WorkflowState.SPEAKERS_DISCOVERED);
    assertThat(result.speakerAnalysisJson()).isNotNull();
    verify(ttsService).analyze(draftSession.storyText());
    verify(sessionRepository).updateSession(any());
  }

  @Test
  @DisplayName("Discover speakers stores analysis JSON in session")
  void discoverSpeakersStoresAnalysisJson() throws JsonProcessingException {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);
    List<SpeakerVoiceAnalysisItem> speakers = List.of(
        new SpeakerVoiceAnalysisItem("Alice", "Protagonist", SpeakerVoice.ACHIRD)
    );
    SpeakerVoiceAnalysisResponse analysis = new SpeakerVoiceAnalysisResponse(speakers);
    String analysisJson = "{\"speakers\":[{\"name\":\"Alice\"}]}";

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));
    when(ttsService.analyze(any())).thenReturn(analysis);
    when(objectMapper.writeValueAsString(analysis)).thenReturn(analysisJson);

    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);
    when(sessionRepository.updateSession(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    workflowService.executeDiscoverSpeakers("session-1", testUser);

    WorkflowSession updated = captor.getValue();
    assertThat(updated.speakerAnalysisJson()).isEqualTo(analysisJson);
  }

  @Test
  @DisplayName("Discover speakers throws when session not found")
  void discoverSpeakersThrowsWhenSessionNotFound() {
    when(sessionRepository.findSessionForUser("missing", testUser.id()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> workflowService.executeDiscoverSpeakers("missing", testUser))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Session not found");
  }

  @Test
  @DisplayName("Discover speakers throws on invalid state transition")
  void discoverSpeakersThrowsOnInvalidTransition() {
    WorkflowSession alreadyDiscoveredSession = createSessionInState(WorkflowState.SPEAKERS_DISCOVERED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(alreadyDiscoveredSession));

    assertThatThrownBy(() -> workflowService.executeDiscoverSpeakers("session-1", testUser))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  @DisplayName("Discover speakers throws on TTS service failure")
  void discoverSpeakersThrowsOnServiceError() {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));
    when(ttsService.analyze(any())).thenThrow(new RuntimeException("TTS service error"));

    assertThatThrownBy(() -> workflowService.executeDiscoverSpeakers("session-1", testUser))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Speaker discovery failed");
  }

  // ========== SPLIT DIALOGUE TESTS ==========

  @Test
  @DisplayName("Split dialogue transitions to DIALOGUE_SPLIT on success")
  void splitDialogueSuccessTransitions() throws JsonProcessingException {
    WorkflowSession discoveredSession = createSessionInState(WorkflowState.SPEAKERS_DISCOVERED);
    List<SpeakerVoiceAnalysisItem> speakers = List.of(
        new SpeakerVoiceAnalysisItem("Alice", "Protagonist", SpeakerVoice.ACHIRD)
    );
    List<SpeakerSplitTurn> turns = List.of(
        new SpeakerSplitTurn("Alice", "Hello, world!")
    );
    SpeakerSplitAnalysisResponse splitAnalysis = new SpeakerSplitAnalysisResponse(turns);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(discoveredSession));
    when(ttsService.split(any(), any())).thenReturn(splitAnalysis);
    when(objectMapper.writeValueAsString(splitAnalysis)).thenReturn("{\"turns\":[]}");
    when(sessionRepository.updateSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession result = workflowService.executeSplitDialogue("session-1", testUser, speakers);

    assertThat(result.state()).isEqualTo(WorkflowState.DIALOGUE_SPLIT);
    assertThat(result.dialogueSplitJson()).isNotNull();
    verify(ttsService).split(discoveredSession.storyText(), speakers);
    verify(sessionRepository).updateSession(any());
  }

  @Test
  @DisplayName("Split dialogue stores split analysis JSON in session")
  void splitDialogueStoresAnalysisJson() throws JsonProcessingException {
    WorkflowSession discoveredSession = createSessionInState(WorkflowState.SPEAKERS_DISCOVERED);
    List<SpeakerVoiceAnalysisItem> speakers = List.of(
        new SpeakerVoiceAnalysisItem("Alice", "Protagonist", SpeakerVoice.ACHIRD)
    );
    List<SpeakerSplitTurn> turns = List.of(
        new SpeakerSplitTurn("Alice", "Hello")
    );
    SpeakerSplitAnalysisResponse splitAnalysis = new SpeakerSplitAnalysisResponse(turns);
    String splitJson = "{\"turns\":[{\"speaker\":\"Alice\",\"text\":\"Hello\"}]}";

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(discoveredSession));
    when(ttsService.split(any(), any())).thenReturn(splitAnalysis);
    when(objectMapper.writeValueAsString(splitAnalysis)).thenReturn(splitJson);

    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);
    when(sessionRepository.updateSession(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    workflowService.executeSplitDialogue("session-1", testUser, speakers);

    WorkflowSession updated = captor.getValue();
    assertThat(updated.dialogueSplitJson()).isEqualTo(splitJson);
  }

  @Test
  @DisplayName("Split dialogue throws on invalid state transition")
  void splitDialogueThrowsOnInvalidTransition() {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));

    List<SpeakerVoiceAnalysisItem> speakers = List.of(
        new SpeakerVoiceAnalysisItem("Alice", "Protagonist", SpeakerVoice.ACHIRD)
    );

    assertThatThrownBy(() -> workflowService.executeSplitDialogue("session-1", testUser, speakers))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  @DisplayName("Split dialogue throws on TTS service failure")
  void splitDialogueThrowsOnServiceError() {
    WorkflowSession discoveredSession = createSessionInState(WorkflowState.SPEAKERS_DISCOVERED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(discoveredSession));
    when(ttsService.split(any(), any())).thenThrow(new RuntimeException("Split service error"));

    assertThatThrownBy(() -> workflowService.executeSplitDialogue("session-1", testUser, List.of()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Dialogue split failed");
  }

  // ========== ANNOTATE DIALOGUE TESTS ==========

  @Test
  @DisplayName("Annotate dialogue transitions to DIALOGUE_ANNOTATED on success")
  void annotateDialogueSuccessTransitions() throws JsonProcessingException {
    WorkflowSession splitSession = createSessionInState(WorkflowState.DIALOGUE_SPLIT);
    List<SpeakerSplitTurn> turns = List.of(
        new SpeakerSplitTurn("Alice", "Hello, world!")
    );
    List<AnnotatedSpeakerTurn> annotatedTurns = List.of(
        new AnnotatedSpeakerTurn("Alice", "[calm] Hello, world!")
    );
    EmotionAnnotationAnalysisResponse annotated = new EmotionAnnotationAnalysisResponse(annotatedTurns);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(splitSession));
    when(ttsService.annotate(any())).thenReturn(annotated);
    when(objectMapper.writeValueAsString(annotated)).thenReturn("{\"turns\":[]}");
    when(sessionRepository.updateSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession result = workflowService.executeAnnotateDialogue("session-1", testUser, turns);

    assertThat(result.state()).isEqualTo(WorkflowState.DIALOGUE_ANNOTATED);
    assertThat(result.annotationJson()).isNotNull();
    verify(ttsService).annotate(turns);
    verify(sessionRepository).updateSession(any());
  }

  @Test
  @DisplayName("Annotate dialogue stores annotation JSON in session")
  void annotateDialogueStoresAnnotationJson() throws JsonProcessingException {
    WorkflowSession splitSession = createSessionInState(WorkflowState.DIALOGUE_SPLIT);
    List<SpeakerSplitTurn> turns = List.of(
        new SpeakerSplitTurn("Alice", "[calm] Hello")
    );
    List<AnnotatedSpeakerTurn> annotatedTurns = List.of(
        new AnnotatedSpeakerTurn("Alice", "[calm] Hello")
    );
    EmotionAnnotationAnalysisResponse annotated = new EmotionAnnotationAnalysisResponse(annotatedTurns);
    String annotationJson = "{\"turns\":[{\"speaker\":\"Alice\",\"text\":\"[calm] Hello\"}]}";

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(splitSession));
    when(ttsService.annotate(any())).thenReturn(annotated);
    when(objectMapper.writeValueAsString(annotated)).thenReturn(annotationJson);

    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);
    when(sessionRepository.updateSession(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    workflowService.executeAnnotateDialogue("session-1", testUser, turns);

    WorkflowSession updated = captor.getValue();
    assertThat(updated.annotationJson()).isEqualTo(annotationJson);
  }

  @Test
  @DisplayName("Annotate dialogue throws on invalid state transition")
  void annotateDialogueThrowsOnInvalidTransition() {
    WorkflowSession configuredSession = createSessionInState(WorkflowState.CONFIGURED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(configuredSession));

    assertThatThrownBy(() -> workflowService.executeAnnotateDialogue("session-1", testUser, List.of()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  @DisplayName("Annotate dialogue throws on TTS service failure")
  void annotateDialogueThrowsOnServiceError() {
    WorkflowSession splitSession = createSessionInState(WorkflowState.DIALOGUE_SPLIT);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(splitSession));
    when(ttsService.annotate(any())).thenThrow(new RuntimeException("Annotation service error"));

    assertThatThrownBy(() -> workflowService.executeAnnotateDialogue("session-1", testUser, List.of()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Dialogue annotation failed");
  }

  // ========== CONFIGURE OUTPUT TESTS ==========

  @Test
  @DisplayName("Configure output transitions to CONFIGURED on success")
  void configureOutputSuccessTransitions() throws JsonProcessingException {
    WorkflowSession annotatedSession = createSessionInState(WorkflowState.DIALOGUE_ANNOTATED);
    Map<String, String> voiceAssignments = Map.of("Alice", "en-US-Neural2-A");

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(annotatedSession));
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"config\":{}}");
    when(sessionRepository.updateSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession result = workflowService.executeConfigureOutput(
        "session-1", testUser,
        "en-US", "gpt-4", "mp3", voiceAssignments
    );

    assertThat(result.state()).isEqualTo(WorkflowState.CONFIGURED);
    assertThat(result.ttsConfigJson()).isNotNull();
    verify(sessionRepository).updateSession(any());
  }

  @Test
  @DisplayName("Configure output stores configuration JSON in session")
  void configureOutputStoresConfigJson() throws JsonProcessingException {
    WorkflowSession annotatedSession = createSessionInState(WorkflowState.DIALOGUE_ANNOTATED);
    Map<String, String> voiceAssignments = Map.of("Alice", "en-US-Neural2-A");
    String configJson = "{\"languageCode\":\"en-US\",\"modelName\":\"gpt-4\",\"audioEncoding\":\"mp3\"}";

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(annotatedSession));
    when(objectMapper.writeValueAsString(any())).thenReturn(configJson);

    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);
    when(sessionRepository.updateSession(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    workflowService.executeConfigureOutput(
        "session-1", testUser,
        "en-US", "gpt-4", "mp3", voiceAssignments
    );

    WorkflowSession updated = captor.getValue();
    assertThat(updated.ttsConfigJson()).isEqualTo(configJson);
  }

  @Test
  @DisplayName("Configure output throws on invalid state transition")
  void configureOutputThrowsOnInvalidTransition() {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));

    assertThatThrownBy(() -> workflowService.executeConfigureOutput(
        "session-1", testUser,
        "en-US", "gpt-4", "mp3", Map.of()
    )).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  @DisplayName("Configure output throws on ObjectMapper failure")
  void configureOutputThrowsOnMapperError() throws JsonProcessingException {
    WorkflowSession annotatedSession = createSessionInState(WorkflowState.DIALOGUE_ANNOTATED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(annotatedSession));
    when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Mapper error"));

    assertThatThrownBy(() -> workflowService.executeConfigureOutput(
        "session-1", testUser,
        "en-US", "gpt-4", "mp3", Map.of()
    )).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Output configuration failed");
  }

  // ========== GENERATE AUDIO TESTS ==========

  @Test
  @DisplayName("Generate audio transitions to AUDIO_GENERATED on success")
  void generateAudioSuccessTransitions() {
    WorkflowSession configuredSession = createSessionInState(WorkflowState.CONFIGURED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(configuredSession));
    when(sessionRepository.updateSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WorkflowSession result = workflowService.executeGenerateAudio("session-1", testUser);

    assertThat(result.state()).isEqualTo(WorkflowState.AUDIO_GENERATED);
    assertThat(result.completedAt()).isNotNull();
    verify(sessionRepository).updateSession(any());
  }

  @Test
  @DisplayName("Generate audio marks workflow as complete")
  void generateAudioMarksComplete() {
    WorkflowSession configuredSession = createSessionInState(WorkflowState.CONFIGURED);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(configuredSession));

    ArgumentCaptor<WorkflowSession> captor = ArgumentCaptor.forClass(WorkflowSession.class);
    when(sessionRepository.updateSession(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

    workflowService.executeGenerateAudio("session-1", testUser);

    WorkflowSession updated = captor.getValue();
    assertThat(updated.completedAt()).isNotNull();
  }

  @Test
  @DisplayName("Generate audio throws on invalid state transition")
  void generateAudioThrowsOnInvalidTransition() {
    WorkflowSession draftSession = createSessionInState(WorkflowState.DRAFT);

    when(sessionRepository.findSessionForUser("session-1", testUser.id()))
        .thenReturn(Optional.of(draftSession));

    assertThatThrownBy(() -> workflowService.executeGenerateAudio("session-1", testUser))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Invalid state transition");
  }

  @Test
  @DisplayName("Generate audio throws when session not found")
  void generateAudioThrowsWhenSessionNotFound() {
    when(sessionRepository.findSessionForUser("missing", testUser.id()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> workflowService.executeGenerateAudio("missing", testUser))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Session not found");
  }

  // ========== HELPER METHODS ==========

  private WorkflowSession createSessionInState(WorkflowState state) {
    return new WorkflowSession(
        "session-1",
        "project-1",
        testUser.id(),
        state,
        "Story text",
        null,
        null,
        null,
        null,
        null,
        Instant.now(),
        Instant.now(),
        null
    );
  }
}
