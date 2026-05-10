package com.example.ttslab.audiobooks.workflow;

/**
 * State machine for audiobook generation workflow.
 * Linear progression from DRAFT through AUDIO_GENERATED.
 * Each state represents a distinct phase of the workflow.
 */
public enum WorkflowState {
    DRAFT,                      // Initial: story text input
    ANALYZING_SPEAKERS,         // Step 1 in progress
    SPEAKERS_DISCOVERED,        // Step 1 complete: speakers identified
    SPLITTING_DIALOGUE,         // Step 2 in progress
    DIALOGUE_SPLIT,             // Step 2 complete: dialogue split into turns
    ANNOTATING_DIALOGUE,        // Step 3 in progress
    DIALOGUE_ANNOTATED,         // Step 3 complete: emotions annotated
    CONFIGURING_OUTPUT,         // Step 4 in progress
    CONFIGURED,                 // Step 4 complete: TTS config set
    RENDERING_AUDIO,            // Step 5 in progress
    AUDIO_GENERATED,            // Step 5 complete: audio ready
    ARCHIVED;                   // Final: workflow archived

    /**
     * Validate that a transition from current state to target state is allowed.
     * Transitions must be linear progression only.
     */
    public boolean canTransitionTo(WorkflowState next) {
        if (next == this) {
            return true; // Can stay in same state
        }
        return this.ordinal() < next.ordinal();
    }

    /**
     * Check if this state represents a completed step.
     */
    public boolean isCompleted() {
        return this.ordinal() >= SPEAKERS_DISCOVERED.ordinal() &&
               this != ANALYZING_SPEAKERS &&
               this != SPLITTING_DIALOGUE &&
               this != ANNOTATING_DIALOGUE &&
               this != CONFIGURING_OUTPUT &&
               this != RENDERING_AUDIO;
    }
}
