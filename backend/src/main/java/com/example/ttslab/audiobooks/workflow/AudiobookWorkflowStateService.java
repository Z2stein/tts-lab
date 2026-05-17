package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudioAssetStatus;
import com.example.ttslab.audiobooks.model.AudioAssetType;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.service.AudiobookLibraryService;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowProductionSettings;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowSnapshotResponse;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStage;
import com.example.ttslab.audiobooks.workflow.AnnotatedSpeakerTurn;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookRepository;
import com.example.ttslab.audiobooks.repository.AudioAssetResponse;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudiobookWorkflowStateService {
    private final AudiobookRepository repository;
    private final AudiobookProjectRepository projectRepository;
    private final SpeakerCharacterRepository speakerCharacterRepository;
    private final AudiobookLibraryService audiobookLibraryService;

    public AudiobookWorkflowStateService(
        AudiobookRepository repository,
        AudiobookProjectRepository projectRepository,
        SpeakerCharacterRepository speakerCharacterRepository,
        AudiobookLibraryService audiobookLibraryService
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.speakerCharacterRepository = speakerCharacterRepository;
        this.audiobookLibraryService = audiobookLibraryService;
    }

    public AudiobookWorkflowSnapshotResponse snapshot(CurrentUser user, String projectId) {
        AudiobookProject project = getProjectForUser(user, projectId);
        List<AudiobookSpeechSegment> previewSegments = repository.findPreviewSpeechSegments(project.getId());
        List<SpeakerVoiceAnalysisItem> speakers = loadSpeakers(project.getId());
        return buildSnapshot(project, previewSegments, speakers);
    }

    public AudiobookWorkflowSnapshotResponse approveCast(CurrentUser user, String projectId) {
        AudiobookProject project = getProjectForUser(user, projectId);
        ensureStageEquals(project, AudiobookWorkflowStage.CAST_REVIEW, "AUDIOBOOK_WORKFLOW_CAST_NOT_READY", "The cast must be created before it can be approved.");
        AudiobookWorkflowSnapshotResponse snapshot = snapshot(user, projectId);
        if (snapshot.speakers().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_WORKFLOW_CAST_NOT_READY", "The cast must be created before it can be approved.");
        }
        updateWorkflowState(project, AudiobookWorkflowStage.CAST_APPROVED, false);
        return snapshot(user, projectId);
    }

    public AudiobookWorkflowSnapshotResponse approveScript(CurrentUser user, String projectId) {
        AudiobookProject project = getProjectForUser(user, projectId);
        ensureStageEquals(project, AudiobookWorkflowStage.SCRIPT_REVIEW, "AUDIOBOOK_WORKFLOW_SCRIPT_NOT_READY", "The script must be created before it can be approved.");
        AudiobookWorkflowSnapshotResponse snapshot = snapshot(user, projectId);
        if (snapshot.scriptTurns().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_WORKFLOW_SCRIPT_NOT_READY", "The script must be created before it can be approved.");
        }
        updateWorkflowState(project, AudiobookWorkflowStage.SCRIPT_APPROVED, false);
        return snapshot(user, projectId);
    }

    public AudiobookWorkflowSnapshotResponse updateProductionSettings(CurrentUser user, String projectId, AudiobookWorkflowProductionSettings productionSettings) {
        AudiobookProject project = getProjectForUser(user, projectId);
        project.setProductionPrompt(productionSettings.prompt());
        project.setProductionLanguageCode(productionSettings.languageCode());
        project.setProductionModelName(productionSettings.modelName());
        project.setProductionAudioEncoding(productionSettings.audioEncoding());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return snapshot(user, projectId);
    }

    @Transactional
    public void markScriptReview(AudiobookProject project) {
        ensureWorkflowProgressAtOrBeyond(project, AudiobookWorkflowStage.CAST_APPROVED, "AUDIOBOOK_WORKFLOW_CAST_NOT_READY", "The cast must be created before the script can be reviewed.");
        updateWorkflowState(project, AudiobookWorkflowStage.SCRIPT_REVIEW, false);
    }

    @Transactional
    public void markPerformanceReady(AudiobookProject project) {
        ensureStageEquals(project, AudiobookWorkflowStage.SCRIPT_APPROVED, "AUDIOBOOK_WORKFLOW_SCRIPT_NOT_READY", "The script must be approved before emotion and pacing can be saved.");
        updateWorkflowState(project, AudiobookWorkflowStage.PERFORMANCE_READY, false);
    }

    @Transactional
    public void markAudioGenerated(AudiobookProject project) {
        ensureWorkflowProgressAtOrBeyond(project, AudiobookWorkflowStage.PERFORMANCE_READY, "AUDIOBOOK_WORKFLOW_PERFORMANCE_NOT_READY", "Emotion and pacing must be saved before audio can be marked as generated.");
        updateWorkflowState(project, AudiobookWorkflowStage.AUDIO_GENERATED, true);
    }

    @Transactional
    public AudiobookWorkflowSnapshotResponse finalizeAudioGeneration(CurrentUser user, String projectId) {
        AudiobookProject project = getProjectForUser(user, projectId);
        AudiobookWorkflowStage currentStage = resolveCurrentWorkflowStage(project);
        if (currentStage.ordinal() < AudiobookWorkflowStage.PERFORMANCE_READY.ordinal()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_WORKFLOW_PERFORMANCE_NOT_READY", "Emotion and pacing must be saved before audio can be marked as generated.");
        }
        if (currentStage != AudiobookWorkflowStage.AUDIO_GENERATED || !project.isAudioAssetsCurrent()) {
            updateWorkflowState(project, AudiobookWorkflowStage.AUDIO_GENERATED, true);
        }
        audiobookLibraryService.mergeAndPersistFullAudio(project);
        return snapshot(user, projectId);
    }

    private AudiobookWorkflowSnapshotResponse buildSnapshot(
        AudiobookProject project,
        List<AudiobookSpeechSegment> previewSegments,
        List<SpeakerVoiceAnalysisItem> speakers
    ) {
        List<AudioAsset> allAssets = repository.findAssets(project.getId());
        List<AudioAssetResponse> audioAssets = allAssets.stream()
            .filter(a -> a.getType() != AudioAssetType.FULL_AUDIOBOOK)
            .map(this::assetResponse).toList();
        String mergedAudioUrl = allAssets.stream()
            .filter(a -> a.getType() == AudioAssetType.FULL_AUDIOBOOK && a.getStatus() == AudioAssetStatus.READY)
            .findFirst()
            .map(a -> "/api/audiobooks/" + a.getProjectId() + "/audio-assets/" + a.getId() + "/stream")
            .orElse(null);
        AudiobookWorkflowStage workflowStage = resolveWorkflowStage(project, previewSegments, speakers);
        boolean audioAssetsCurrent = project.isAudioAssetsCurrent() && !audioAssets.isEmpty();
        boolean performanceNotesStale = previewSegments.stream()
            .anyMatch(segment -> segment.getReviewStatus() == AudiobookSpeechSegmentReviewStatus.NEEDS_CHANGES);
        List<SpeakerSplitTurn> scriptTurns = previewSegments.stream()
            .map(segment -> new SpeakerSplitTurn(resolveSpeakerName(segment), resolveOriginalText(segment)))
            .toList();
        List<AnnotatedSpeakerTurn> annotatedTurns = previewSegments.stream()
            .filter(segment -> segment.getStyledText() != null && !segment.getStyledText().isBlank())
            .map(segment -> new AnnotatedSpeakerTurn(resolveSpeakerName(segment), segment.getStyledText().trim()))
            .toList();

        return new AudiobookWorkflowSnapshotResponse(
            project.getId(),
            project.getTitle(),
            project.getStoryText(),
            defaultString(project.getSourceLanguageCode(), SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE),
            workflowStage,
            speakers,
            scriptTurns,
            annotatedTurns,
            new AudiobookWorkflowProductionSettings(
                defaultString(project.getProductionPrompt(), "An immersive audiobook performance with a clear narrator and distinct character voices."),
                defaultString(project.getProductionLanguageCode(), "en-US"),
                defaultString(project.getProductionModelName(), "gemini-3.1-flash-tts-preview"),
                defaultString(project.getProductionAudioEncoding(), "MP3")
            ),
            audioAssets,
            audioAssetsCurrent,
            performanceNotesStale,
            mergedAudioUrl
        );
    }

    private List<SpeakerVoiceAnalysisItem> loadSpeakers(String projectId) {
        return speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
            .map(character -> new SpeakerVoiceAnalysisItem(character.getSpeakerName(), character.getRoleDescription(), character.getVoiceSuggestion()))
            .toList();
    }

    private AudiobookProject getProjectForUser(CurrentUser user, String projectId) {
        return repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));
    }

    private void updateWorkflowState(AudiobookProject project, AudiobookWorkflowStage workflowStage, boolean audioAssetsCurrent) {
        project.setWorkflowStage(workflowStage);
        project.setAudioAssetsCurrent(audioAssetsCurrent);
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
    }

    private void ensureStageEquals(AudiobookProject project, AudiobookWorkflowStage expectedStage, String code, String message) {
        AudiobookWorkflowStage currentStage = resolveCurrentWorkflowStage(project);
        if (currentStage != expectedStage) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
    }

    public void ensureScriptReviewReady(AudiobookProject project) {
        ensureWorkflowProgressAtOrBeyond(project, AudiobookWorkflowStage.CAST_APPROVED, "AUDIOBOOK_WORKFLOW_CAST_NOT_READY", "The cast must be created before the script can be reviewed.");
    }

    public void ensurePerformanceNotesReady(AudiobookProject project) {
        ensureStageEquals(project, AudiobookWorkflowStage.SCRIPT_APPROVED, "AUDIOBOOK_WORKFLOW_SCRIPT_NOT_READY", "The script must be approved before emotion and pacing can be saved.");
    }

    public void ensureAudioGenerationReady(AudiobookProject project) {
        ensureWorkflowProgressAtOrBeyond(project, AudiobookWorkflowStage.PERFORMANCE_READY, "AUDIOBOOK_WORKFLOW_PERFORMANCE_NOT_READY", "Emotion and pacing must be saved before audio can be generated.");
    }

    private void ensureWorkflowProgressAtOrBeyond(AudiobookProject project, AudiobookWorkflowStage minimumStage, String code, String message) {
        AudiobookWorkflowStage currentStage = resolveCurrentWorkflowStage(project);
        if (currentStage.ordinal() < minimumStage.ordinal()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
    }

    private AudiobookWorkflowStage resolveCurrentWorkflowStage(AudiobookProject project) {
        if (project.getWorkflowStage() != null) {
            return project.getWorkflowStage();
        }
        return resolveWorkflowStage(project, repository.findPreviewSpeechSegments(project.getId()), loadSpeakers(project.getId()));
    }

    private String resolveSpeakerName(AudiobookSpeechSegment segment) {
        SpeakerCharacter character = segment.getCharacter();
        if (character != null) {
            String speakerName = character.getSpeakerName();
            if (speakerName != null && !speakerName.isBlank()) {
                return speakerName.trim();
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_WORKFLOW_SNAPSHOT_INVALID", "The audiobook workflow snapshot is no longer valid.");
    }

    private String resolveOriginalText(AudiobookSpeechSegment segment) {
        if (segment.getOriginalText() == null || segment.getOriginalText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_WORKFLOW_SNAPSHOT_INVALID", "The audiobook workflow snapshot is no longer valid.");
        }
        return segment.getOriginalText().trim();
    }

    private AudiobookWorkflowStage resolveWorkflowStage(
        AudiobookProject project,
        List<AudiobookSpeechSegment> previewSegments,
        List<SpeakerVoiceAnalysisItem> speakers
    ) {
        if (project.getWorkflowStage() != null) {
            return project.getWorkflowStage();
        }
        if (project.isAudioAssetsCurrent() && !repository.findAssets(project.getId()).isEmpty()) {
            return AudiobookWorkflowStage.AUDIO_GENERATED;
        }
        boolean hasStyledText = previewSegments.stream().anyMatch(segment -> segment.getStyledText() != null && !segment.getStyledText().isBlank());
        if (hasStyledText) {
            return AudiobookWorkflowStage.PERFORMANCE_READY;
        }
        if (!previewSegments.isEmpty()) {
            return AudiobookWorkflowStage.SCRIPT_REVIEW;
        }
        if (!speakers.isEmpty()) {
            return AudiobookWorkflowStage.CAST_APPROVED;
        }
        return AudiobookWorkflowStage.CAST_REVIEW;
    }

    private String defaultString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private AudioAssetResponse assetResponse(AudioAsset asset) {
        String speakerName = null;
        if (asset.getSegment() != null && asset.getSegment().getCharacter() != null) {
            speakerName = asset.getSegment().getCharacter().getSpeakerName();
        }
        return new AudioAssetResponse(
            asset.getId(),
            asset.getSpeechSegmentId(),
            asset.getType(),
            asset.getVersion(),
            asset.getFilename(),
            asset.getContentType(),
            asset.getSizeBytes(),
            asset.getDurationSeconds(),
            asset.getStatus(),
            asset.getCreatedAt(),
            "/api/audiobooks/" + asset.getProjectId() + "/audio-assets/" + asset.getId() + "/download",
            "/api/audiobooks/" + asset.getProjectId() + "/audio-assets/" + asset.getId() + "/stream",
            speakerName
        );
    }
}
