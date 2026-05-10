package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.workflow.WorkflowDtos.*;
import com.example.ttslab.prompts.CurrentUserResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.net.URI;

/**
 * REST API for audiobook generation workflow state machine.
 * 7 endpoints: create, get, + 5 step execution endpoints.
 */
@RestController
@RequestMapping("/api/audiobooks/workflows")
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowSessionRepository sessionRepository;
    private final CurrentUserResolver currentUserResolver;

    public WorkflowController(WorkflowService workflowService, WorkflowSessionRepository sessionRepository, CurrentUserResolver currentUserResolver) {
        this.workflowService = workflowService;
        this.sessionRepository = sessionRepository;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * POST /api/audiobooks/workflows
     * Create a new audiobook generation workflow.
     */
    @PostMapping
    public ResponseEntity<WorkflowSessionResponse> createWorkflow(
        @RequestBody @Valid CreateWorkflowRequest request,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.initiateWorkflow(user, request.storyText());
            return ResponseEntity
                .created(URI.create("/api/audiobooks/workflows/" + session.id()))
                .body(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Failed to create workflow", ex);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/audiobooks/workflows/{sessionId}
     * Fetch current workflow state.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<WorkflowSessionResponse> getWorkflow(
        @PathVariable String sessionId,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = sessionRepository
                .findSessionForUser(sessionId, user.id())
                .orElse(null);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Failed to fetch workflow", ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/audiobooks/workflows/{sessionId}/discover-speakers
     * Execute Step 1: Discover speakers.
     */
    @PostMapping("/{sessionId}/discover-speakers")
    public ResponseEntity<WorkflowSessionResponse> discoverSpeakers(
        @PathVariable String sessionId,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.executeDiscoverSpeakers(sessionId, user);
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Speaker discovery failed", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * POST /api/audiobooks/workflows/{sessionId}/split-dialogue
     * Execute Step 2: Split dialogue.
     */
    @PostMapping("/{sessionId}/split-dialogue")
    public ResponseEntity<WorkflowSessionResponse> splitDialogue(
        @PathVariable String sessionId,
        @RequestBody @Valid SplitDialogueRequest request,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.executeSplitDialogue(sessionId, user, request.speakers());
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Dialogue split failed", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * POST /api/audiobooks/workflows/{sessionId}/annotate-dialogue
     * Execute Step 3: Annotate dialogue.
     */
    @PostMapping("/{sessionId}/annotate-dialogue")
    public ResponseEntity<WorkflowSessionResponse> annotateDialogue(
        @PathVariable String sessionId,
        @RequestBody @Valid AnnotateDialogueRequest request,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.executeAnnotateDialogue(sessionId, user, request.turns());
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Dialogue annotation failed", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * POST /api/audiobooks/workflows/{sessionId}/configure-output
     * Execute Step 4: Configure TTS output.
     */
    @PostMapping("/{sessionId}/configure-output")
    public ResponseEntity<WorkflowSessionResponse> configureOutput(
        @PathVariable String sessionId,
        @RequestBody @Valid ConfigureOutputRequest request,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.executeConfigureOutput(
                sessionId, user,
                request.languageCode(),
                request.modelName(),
                request.audioEncoding(),
                request.voiceAssignments()
            );
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Output configuration failed", ex);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/audiobooks/workflows/{sessionId}/generate-audio
     * Execute Step 5: Generate audio.
     */
    @PostMapping("/{sessionId}/generate-audio")
    public ResponseEntity<WorkflowSessionResponse> generateAudio(
        @PathVariable String sessionId,
        Authentication authentication
    ) {
        try {
            CurrentUser user = currentUserResolver.resolve(authentication);
            WorkflowSession session = workflowService.executeGenerateAudio(sessionId, user);
            return ResponseEntity.ok(WorkflowSessionResponse.from(session));
        } catch (Exception ex) {
            log.error("Audio generation failed", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
