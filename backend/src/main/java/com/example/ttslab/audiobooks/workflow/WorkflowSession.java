package com.example.ttslab.audiobooks.workflow;

import java.time.Instant;

/**
 * Represents a single audiobook generation workflow session.
 * Stores workflow state and all intermediate artifacts (JSON) for persistence and audit.
 */
public record WorkflowSession(
    String id,
    String projectId,
    String userId,
    WorkflowState state,

    // Intermediate artifact storage (deserialized as needed)
    String storyText,
    String speakerAnalysisJson,
    String dialogueSplitJson,
    String annotationJson,
    String ttsConfigJson,
    String renderPlanJson,

    // Timestamps for audit trail
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {

    /**
     * Create a new workflow in DRAFT state.
     */
    public static WorkflowSession draft(String projectId, String userId, String storyText) {
        Instant now = Instant.now();
        return new WorkflowSession(
            java.util.UUID.randomUUID().toString(),
            projectId,
            userId,
            WorkflowState.DRAFT,
            storyText, null, null, null, null, null,
            now, now, null
        );
    }

    /**
     * Create a new session with updated state and optionally new artifact.
     */
    public WorkflowSession withState(WorkflowState newState) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, this.speakerAnalysisJson, this.dialogueSplitJson,
            this.annotationJson, this.ttsConfigJson, this.renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Create new session with updated speaker analysis.
     */
    public WorkflowSession withSpeakerAnalysis(WorkflowState newState, String analysisJson) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, analysisJson, this.dialogueSplitJson,
            this.annotationJson, this.ttsConfigJson, this.renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Create new session with updated dialogue split.
     */
    public WorkflowSession withDialogueSplit(WorkflowState newState, String splitJson) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, this.speakerAnalysisJson, splitJson,
            this.annotationJson, this.ttsConfigJson, this.renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Create new session with updated annotations.
     */
    public WorkflowSession withAnnotations(WorkflowState newState, String annotationJson) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, this.speakerAnalysisJson, this.dialogueSplitJson,
            annotationJson, this.ttsConfigJson, this.renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Create new session with TTS configuration.
     */
    public WorkflowSession withTtsConfig(WorkflowState newState, String configJson) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, this.speakerAnalysisJson, this.dialogueSplitJson,
            this.annotationJson, configJson, this.renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Create new session with render plan.
     */
    public WorkflowSession withRenderPlan(WorkflowState newState, String renderPlanJson) {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, newState,
            this.storyText, this.speakerAnalysisJson, this.dialogueSplitJson,
            this.annotationJson, this.ttsConfigJson, renderPlanJson,
            this.createdAt, Instant.now(), this.completedAt
        );
    }

    /**
     * Mark workflow as complete.
     */
    public WorkflowSession complete() {
        return new WorkflowSession(
            this.id, this.projectId, this.userId, WorkflowState.AUDIO_GENERATED,
            this.storyText, this.speakerAnalysisJson, this.dialogueSplitJson,
            this.annotationJson, this.ttsConfigJson, this.renderPlanJson,
            this.createdAt, Instant.now(), Instant.now()
        );
    }
}
