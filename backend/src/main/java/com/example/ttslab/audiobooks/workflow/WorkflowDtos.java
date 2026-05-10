package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.projects.ttsworkbench.SpeakerVoiceAnalysisItem;
import com.example.ttslab.projects.ttsworkbench.SpeakerSplitTurn;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs for workflow API requests and responses.
 */
public class WorkflowDtos {

    // ============ REQUEST DTOs ============

    public record CreateWorkflowRequest(
        @NotBlank(message = "storyText is required") String storyText
    ) {}

    public record SplitDialogueRequest(
        List<SpeakerVoiceAnalysisItem> speakers
    ) {}

    public record AnnotateDialogueRequest(
        List<SpeakerSplitTurn> turns
    ) {}

    public record ConfigureOutputRequest(
        String languageCode,
        String modelName,
        String audioEncoding,
        Map<String, String> voiceAssignments
    ) {}

    // ============ RESPONSE DTOs ============

    public record WorkflowSessionResponse(
        String id,
        String projectId,
        String userId,
        WorkflowState state,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        List<String> availableActions
    ) {
        /**
         * Create response from WorkflowSession.
         */
        public static WorkflowSessionResponse from(WorkflowSession session) {
            List<String> actions = switch(session.state()) {
                case DRAFT -> List.of("discover-speakers");
                case SPEAKERS_DISCOVERED -> List.of("split-dialogue");
                case DIALOGUE_SPLIT -> List.of("annotate-dialogue");
                case DIALOGUE_ANNOTATED -> List.of("configure-output");
                case CONFIGURED -> List.of("generate-audio");
                case AUDIO_GENERATED -> List.of("download");
                default -> List.of();
            };

            return new WorkflowSessionResponse(
                session.id(),
                session.projectId(),
                session.userId(),
                session.state(),
                session.createdAt(),
                session.updatedAt(),
                session.completedAt(),
                actions
            );
        }
    }

    public record ErrorResponse(
        int status,
        String code,
        String message,
        String requestId
    ) {}
}
