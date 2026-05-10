package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.AudiobookRepository;
import com.example.ttslab.projects.ttsworkbench.EmotionAnnotationAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisItem;
import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitAnalysisResponse;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitTurn;
import com.example.ttslab.projects.ttsworkbench.TtsWorkbenchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration service for audiobook generation workflow.
 * Manages state transitions and persists intermediate artifacts.
 */
@Service
@Slf4j
public class WorkflowService {

    private final WorkflowSessionRepository sessionRepository;
    private final AudiobookRepository audiobookRepository;
    private final TtsWorkbenchService ttsService;
    private final ObjectMapper objectMapper;

    public WorkflowService(
        WorkflowSessionRepository sessionRepository,
        AudiobookRepository audiobookRepository,
        TtsWorkbenchService ttsService,
        ObjectMapper objectMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.audiobookRepository = audiobookRepository;
        this.ttsService = ttsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 0: Initiate workflow by creating session.
     */
    @Transactional
    public WorkflowSession initiateWorkflow(CurrentUser user, String storyText) {
        // Create workflow session without external project reference for now
        String projectId = UUID.randomUUID().toString();
        WorkflowSession session = WorkflowSession.draft(projectId, user.id(), storyText);
        sessionRepository.createSession(session);

        log.info("Initiated workflow {} for user {}", session.id(), user.id());
        return session;
    }

    /**
     * Step 1: Discover speakers from story text.
     */
    @Transactional
    public WorkflowSession executeDiscoverSpeakers(String sessionId, CurrentUser user) {
        WorkflowSession session = getAndVerifySession(sessionId, user.id());
        validateTransition(session, WorkflowState.ANALYZING_SPEAKERS);

        try {
            // Call TTS service
            SpeakerVoiceAnalysisResponse analysis = ttsService.analyze(session.storyText());
            String analysisJson = objectMapper.writeValueAsString(analysis);

            // Transition to SPEAKERS_DISCOVERED
            WorkflowSession updated = session.withSpeakerAnalysis(WorkflowState.SPEAKERS_DISCOVERED, analysisJson);
            sessionRepository.updateSession(updated);

            log.info("Discovered {} speakers for workflow {}", analysis.speakers().size(), sessionId);
            return updated;

        } catch (Exception ex) {
            log.error("Speaker discovery failed", ex);
            throw new RuntimeException("Speaker discovery failed", ex);
        }
    }

    /**
     * Step 2: Split dialogue into turns.
     */
    @Transactional
    public WorkflowSession executeSplitDialogue(String sessionId, CurrentUser user,
                                               List<SpeakerVoiceAnalysisItem> speakers) {
        WorkflowSession session = getAndVerifySession(sessionId, user.id());
        validateTransition(session, WorkflowState.SPLITTING_DIALOGUE);

        try {
            // Call TTS service
            SpeakerSplitAnalysisResponse splitAnalysis = ttsService.split(session.storyText(), speakers);
            String splitJson = objectMapper.writeValueAsString(splitAnalysis);

            // TODO: Create SpeechSegment records in future phase

            // Transition to DIALOGUE_SPLIT
            WorkflowSession updated = session.withDialogueSplit(WorkflowState.DIALOGUE_SPLIT, splitJson);
            sessionRepository.updateSession(updated);

            log.info("Split dialogue into {} turns for workflow {}", splitAnalysis.turns().size(), sessionId);
            return updated;

        } catch (Exception ex) {
            log.error("Dialogue split failed", ex);
            throw new RuntimeException("Dialogue split failed", ex);
        }
    }

    /**
     * Step 3: Annotate dialogue with emotions.
     */
    @Transactional
    public WorkflowSession executeAnnotateDialogue(String sessionId, CurrentUser user,
                                                  List<SpeakerSplitTurn> turns) {
        WorkflowSession session = getAndVerifySession(sessionId, user.id());
        validateTransition(session, WorkflowState.ANNOTATING_DIALOGUE);

        try {
            // Call TTS service
            EmotionAnnotationAnalysisResponse annotated = ttsService.annotate(turns);
            String annotationJson = objectMapper.writeValueAsString(annotated);

            // TODO: Update SpeechSegment.annotatedText in future phase

            // Transition to DIALOGUE_ANNOTATED
            WorkflowSession updated = session.withAnnotations(WorkflowState.DIALOGUE_ANNOTATED, annotationJson);
            sessionRepository.updateSession(updated);

            log.info("Annotated dialogue for workflow {}", sessionId);
            return updated;

        } catch (Exception ex) {
            log.error("Dialogue annotation failed", ex);
            throw new RuntimeException("Dialogue annotation failed", ex);
        }
    }

    /**
     * Step 4: Configure TTS output settings.
     */
    @Transactional
    public WorkflowSession executeConfigureOutput(String sessionId, CurrentUser user,
                                                 String languageCode, String modelName,
                                                 String audioEncoding, Map<String, String> voiceAssignments) {
        WorkflowSession session = getAndVerifySession(sessionId, user.id());
        validateTransition(session, WorkflowState.CONFIGURING_OUTPUT);

        try {
            // Store config JSON
            String configJson = objectMapper.writeValueAsString(
                Map.of("languageCode", languageCode, "modelName", modelName, "audioEncoding", audioEncoding,
                       "voiceAssignments", voiceAssignments)
            );

            // Transition to CONFIGURED
            WorkflowSession updated = session.withTtsConfig(WorkflowState.CONFIGURED, configJson);
            sessionRepository.updateSession(updated);

            log.info("Configured output for workflow {} (lang={}, model={}, encoding={})",
                sessionId, languageCode, modelName, audioEncoding);
            return updated;

        } catch (Exception ex) {
            log.error("Output configuration failed", ex);
            throw new RuntimeException("Output configuration failed", ex);
        }
    }

    /**
     * Step 5: Generate audio.
     * Placeholder - actual audio generation will be added later.
     */
    @Transactional
    public WorkflowSession executeGenerateAudio(String sessionId, CurrentUser user) {
        WorkflowSession session = getAndVerifySession(sessionId, user.id());
        validateTransition(session, WorkflowState.RENDERING_AUDIO);

        try {
            // TODO: Implement actual audio generation

            // Mark as complete for now
            WorkflowSession completed = session.complete();
            sessionRepository.updateSession(completed);

            log.info("Generated audio for workflow {}", sessionId);
            return completed;

        } catch (Exception ex) {
            log.error("Audio generation failed", ex);
            throw new RuntimeException("Audio generation failed", ex);
        }
    }

    // ============ HELPER METHODS ============

    private WorkflowSession getAndVerifySession(String sessionId, String userId) {
        return sessionRepository.findSessionForUser(sessionId, userId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
    }

    private void validateTransition(WorkflowSession session, WorkflowState nextState) {
        if (!session.state().canTransitionTo(nextState)) {
            throw new RuntimeException(
                "Invalid state transition from " + session.state() + " to " + nextState
            );
        }
    }
}
