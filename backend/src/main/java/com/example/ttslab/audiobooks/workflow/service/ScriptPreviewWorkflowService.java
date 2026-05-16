package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStateService;
import com.example.ttslab.audiobooks.workflow.ScriptPreviewSaveRequest;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptPreviewWorkflowService {
    private final AudiobookLibraryService audiobookLibraryService;
    private final AudiobookWorkflowStateService audiobookWorkflowStateService;
    private final EmotionAnnotationPersistenceService emotionAnnotationPersistenceService;

    public ScriptPreviewWorkflowService(
        AudiobookLibraryService audiobookLibraryService,
        AudiobookWorkflowStateService audiobookWorkflowStateService,
        EmotionAnnotationPersistenceService emotionAnnotationPersistenceService
    ) {
        this.audiobookLibraryService = audiobookLibraryService;
        this.audiobookWorkflowStateService = audiobookWorkflowStateService;
        this.emotionAnnotationPersistenceService = emotionAnnotationPersistenceService;
    }

    @Transactional
    public SpeakerSplitAnalysisResponse saveScriptPreview(CurrentUser user, ScriptPreviewSaveRequest request) {
        AudiobookProject project = audiobookLibraryService.getProjectForUser(request.projectId(), user);
        audiobookWorkflowStateService.ensureScriptReviewReady(project);

        List<SpeakerSplitTurn> savedTurns = emotionAnnotationPersistenceService.saveScriptPreviewTurns(project, request.turns());

        audiobookWorkflowStateService.markScriptReview(project);
        return new SpeakerSplitAnalysisResponse(savedTurns);
    }
}
