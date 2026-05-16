package com.example.ttslab.audiobooks.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStateService;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStage;
import com.example.ttslab.audiobooks.workflow.ScriptPreviewSaveRequest;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.error.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ScriptPreviewWorkflowServiceTest {
    @Test
    void saveScriptPreviewValidatesPersistsAndReopensScriptReviewInOrder() {
        AudiobookLibraryService audiobookLibraryService = mock(AudiobookLibraryService.class);
        AudiobookWorkflowStateService audiobookWorkflowStateService = mock(AudiobookWorkflowStateService.class);
        EmotionAnnotationPersistenceService emotionAnnotationPersistenceService = mock(EmotionAnnotationPersistenceService.class);
        ScriptPreviewWorkflowService service = new ScriptPreviewWorkflowService(
            audiobookLibraryService,
            audiobookWorkflowStateService,
            emotionAnnotationPersistenceService
        );

        CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        ScriptPreviewSaveRequest request = new ScriptPreviewSaveRequest(
            "project-1",
            List.of(new SpeakerSplitTurn("Narrator", "The opening line."))
        );
        List<SpeakerSplitTurn> savedTurns = List.of(new SpeakerSplitTurn("Narrator", "The opening line."));

        when(audiobookLibraryService.getProjectForUser(eq("project-1"), eq(user))).thenReturn(project);
        when(emotionAnnotationPersistenceService.saveScriptPreviewTurns(eq(project), eq(request.turns()))).thenReturn(savedTurns);

        SpeakerSplitAnalysisResponse response = service.saveScriptPreview(user, request);

        org.mockito.InOrder order = inOrder(audiobookLibraryService, audiobookWorkflowStateService, emotionAnnotationPersistenceService);
        order.verify(audiobookLibraryService).getProjectForUser("project-1", user);
        order.verify(audiobookWorkflowStateService).ensureScriptReviewReady(project);
        order.verify(emotionAnnotationPersistenceService).saveScriptPreviewTurns(project, request.turns());
        order.verify(audiobookWorkflowStateService).markScriptReview(project);

        assertThat(response).isEqualTo(new SpeakerSplitAnalysisResponse(savedTurns));
    }

    @Test
    void saveScriptPreviewDoesNotPersistWhenStageGateFails() {
        AudiobookLibraryService audiobookLibraryService = mock(AudiobookLibraryService.class);
        AudiobookWorkflowStateService audiobookWorkflowStateService = mock(AudiobookWorkflowStateService.class);
        EmotionAnnotationPersistenceService emotionAnnotationPersistenceService = mock(EmotionAnnotationPersistenceService.class);
        ScriptPreviewWorkflowService service = new ScriptPreviewWorkflowService(
            audiobookLibraryService,
            audiobookWorkflowStateService,
            emotionAnnotationPersistenceService
        );

        CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        project.setWorkflowStage(AudiobookWorkflowStage.CAST_APPROVED);
        ScriptPreviewSaveRequest request = new ScriptPreviewSaveRequest(
            "project-1",
            List.of(new SpeakerSplitTurn("Narrator", "The opening line."))
        );

        when(audiobookLibraryService.getProjectForUser(eq("project-1"), eq(user))).thenReturn(project);
        doThrow(new ApiException(
            HttpStatus.BAD_REQUEST,
            "AUDIOBOOK_WORKFLOW_CAST_NOT_READY",
            "The cast must be created before the script can be reviewed."
        )).when(audiobookWorkflowStateService).ensureScriptReviewReady(project);

        assertThatThrownBy(() -> service.saveScriptPreview(user, request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("The cast must be created before the script can be reviewed.");

        verify(audiobookLibraryService).getProjectForUser("project-1", user);
        verify(audiobookWorkflowStateService).ensureScriptReviewReady(project);
        verifyNoInteractions(emotionAnnotationPersistenceService);
        verify(audiobookWorkflowStateService, never()).markScriptReview(any());
    }
}
