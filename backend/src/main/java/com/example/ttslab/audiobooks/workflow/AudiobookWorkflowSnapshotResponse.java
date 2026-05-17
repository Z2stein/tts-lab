package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.repository.AudioAssetResponse;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import java.util.List;

public record AudiobookWorkflowSnapshotResponse(
    String projectId,
    String title,
    String storyText,
    String sourceLanguageCode,
    AudiobookWorkflowStage workflowStage,
    List<SpeakerVoiceAnalysisItem> speakers,
    List<SpeakerSplitTurn> scriptTurns,
    List<AnnotatedSpeakerTurn> annotatedTurns,
    AudiobookWorkflowProductionSettings productionSettings,
    List<AudioAssetResponse> audioAssets,
    boolean audioAssetsCurrent,
    boolean performanceNotesStale,
    String mergedAudioUrl
) {
}
