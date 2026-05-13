package com.example.ttslab.audiobooks.service;

import com.example.ttslab.audiobooks.dto.AudiobookDetailResponse;
import com.example.ttslab.audiobooks.dto.AudiobookSpeechSegmentResponse;
import com.example.ttslab.audiobooks.dto.AudiobookSummaryResponse;
import com.example.ttslab.audiobooks.model.*;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.repository.*;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.TtsAudioFile;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowStage;
import com.example.ttslab.storage.FileStorageService;
import com.example.ttslab.storage.StorageKeyBuilder;
import com.example.ttslab.storage.StoredFile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudiobookLibraryService {
    private final AudiobookRepository repository;
    private final AudiobookProjectRepository projectRepository;
    private final AudiobookSpeechSegmentRepository segmentRepository;
    private final AudioAssetRepository assetRepository;
    private final FileStorageService fileStorageService;
    private final StorageKeyBuilder storageKeyBuilder;
    private final AudiobookMetadataCalculator metadataCalculator;

    public AudiobookLibraryService(
        AudiobookRepository repository,
        AudiobookProjectRepository projectRepository,
        AudiobookSpeechSegmentRepository segmentRepository,
        AudioAssetRepository assetRepository,
        FileStorageService fileStorageService,
        StorageKeyBuilder storageKeyBuilder,
        AudiobookMetadataCalculator metadataCalculator
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.assetRepository = assetRepository;
        this.fileStorageService = fileStorageService;
        this.storageKeyBuilder = storageKeyBuilder;
        this.metadataCalculator = metadataCalculator;
    }

    public AudiobookSummaryResponse list(CurrentUser user) {
        List<AudiobookSummaryResponse.AudiobookSummaryItem> items = repository.findProjectsForUser(user.id()).stream()
            .map(project -> {
                var assets = repository.findAssets(project.getId());

                // CALCULATE metadata on-demand from audio assets
                // This ensures metadata is always fresh and accurate, never stale
                int calculatedSpeechSegmentCount = metadataCalculator.calculateSpeechSegmentCount(project.getId());
                int calculatedSpeakerCount = metadataCalculator.calculateSpeakerCount(project.getId());
                int calculatedDuration = metadataCalculator.calculateTotalDurationSeconds(project.getId());

                return new AudiobookSummaryResponse.AudiobookSummaryItem(
                    project.getId(),
                    project.getTitle(),
                    project.getStatus(),
                    calculatedSpeechSegmentCount,
                    calculatedSpeakerCount,
                    calculatedDuration,
                    project.getUpdatedAt(),
                    assets.stream().map(this::assetResponse).toList()
                );
            })
            .toList();
        return new AudiobookSummaryResponse(items);
    }

    public AudiobookDetailResponse detail(CurrentUser user, String projectId) {
        AudiobookProject project = repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));
        return toDetailResponse(project);
    }

    @Transactional
    public AudiobookDetailResponse updateTitle(CurrentUser user, String projectId, String title) {
        AudiobookProject project = repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));
        project.setTitle(normalizeTitle(title));
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toDetailResponse(project);
    }

    private AudiobookDetailResponse toDetailResponse(AudiobookProject project) {
        return new AudiobookDetailResponse(
            project.getId(),
            project.getTitle(),
            project.getStatus(),
            project.getSpeechSegmentCount(),
            project.getSpeakerCount(),
            project.getTotalDurationSeconds(),
            project.getCreatedAt(),
            project.getUpdatedAt(),
            repository.findPreviewSpeechSegments(project.getId()).stream()
                .map(speechSegment -> new AudiobookSpeechSegmentResponse(
                    speechSegment.getId(),
                    speechSegment.getOrderIndex(),
                    speechSegment.getTitle(),
                    speechSegment.getReviewStatus(),
                    speechSegment.getDurationSeconds(),
                    speechSegment.getSpeakerName(),
                    speechSegment.getSpeakerRoleDescription(),
                    speechSegment.getVoiceName(),
                    speechSegment.getPerformanceDirections(),
                    speechSegment.getStyledText()
                ))
                .toList(),
            repository.findAssets(project.getId()).stream().map(this::assetResponse).toList()
        );
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_TITLE_INVALID", "The project title is required.");
        }
        String trimmed = title.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_TITLE_INVALID", "The project title is required.");
        }
        if (trimmed.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIOBOOK_TITLE_INVALID", "The project title is too long.");
        }
        return trimmed;
    }

    public AudioAsset assetForDownload(CurrentUser user, String projectId, String assetId) {
        AudioAsset asset = repository.findAssetForUser(projectId, assetId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIO_ASSET_NOT_FOUND", "The requested audio asset was not found."));
        if (asset.getStatus() != AudioAssetStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "AUDIO_ASSET_NOT_READY", "The requested audio asset is not ready yet.");
        }
        return asset;
    }

    public StoredFile read(AudioAsset asset) {
        try {
            return fileStorageService.get(asset.getStorageKey(), asset.getContentType(), asset.getSizeBytes());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_READ_FAILED", "The audio file could not be read.", null, ex);
        }
    }

    @Transactional
    public AudiobookProject createProjectForGeneration(CurrentUser user) {
        String projectId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "Generated audiobook",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            now,
            now
        );
        project.setWorkflowStage(AudiobookWorkflowStage.AUDIO_GENERATED);
        project.setProductionPrompt("An immersive audiobook performance with a clear narrator and distinct character voices.");
        project.setProductionLanguageCode("en-US");
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project.setProductionAudioEncoding("MP3");
        projectRepository.save(project);
        return project;
    }

    public AudiobookProject getProjectForUser(String projectId, CurrentUser user) {
        return repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The audiobook project was not found."));
    }

    public AudioAsset persistAudioAsset(AudiobookProject project, TtsAudioFile audioFile, int speechSegmentCount, int version, Integer speakerCount, Integer totalDurationSeconds) {
        return persistAudioAsset(project, audioFile, speechSegmentCount, version, speakerCount, totalDurationSeconds, null, null, null, null);
    }

    @Transactional
    public AudioAsset persistAudioAsset(
        AudiobookProject project,
        TtsAudioFile audioFile,
        int speechSegmentCount,
        int version,
        Integer speakerCount,
        Integer totalDurationSeconds,
        String speakerName,
        String speakerRoleDescription,
        String voiceName,
        String performanceDirections
    ) {
        String assetId = UUID.randomUUID().toString();
        String speechSegmentId = UUID.randomUUID().toString();
        String storageKey = storageKeyBuilder.projectAsset(project.getUserId(), project.getId(), AudioAssetType.PREVIEW_MP3, version, "mp3");

        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        // IMPORTANT: Do NOT update project speech segment counts or other metadata directly
        // Metadata is calculated on-demand by AudiobookMetadataCalculator from audio assets
        // This prevents stale metadata issues that occur with persisted values

        // Update project timestamp
        project.setUpdatedAt(Instant.now());
        project.setStatus(AudiobookProjectStatus.NEEDS_REVIEW);
        project.setWorkflowStage(AudiobookWorkflowStage.AUDIO_GENERATED);
        projectRepository.save(project);

        String speechSegmentTitle = speakerName != null && !speakerName.isBlank() ? speakerName : "Generated speech segment";
        Instant now = Instant.now();
        AudiobookSpeechSegment speechSegment = new AudiobookSpeechSegment(
            speechSegmentId,
            project,
            0,
            speechSegmentTitle,
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            now,
            now,
            speakerName,
            speakerRoleDescription,
            voiceName,
            performanceDirections,
            null,
            null,
            null
        );
        speechSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.GENERATED_AUDIO);
        AudioAsset asset = new AudioAsset(
            assetId,
            project,
            speechSegment,
            AudioAssetType.PREVIEW_MP3,
            version,
            storageKey,
            audioFile.filename(),
            audioFile.contentType(),
            audioFile.content().length,
            totalDurationSeconds,
            AudioAssetStatus.READY,
            now
        );
        segmentRepository.save(speechSegment);
        assetRepository.save(asset);
        return asset;
    }

    @Transactional
    public void persistGeneratedPreview(CurrentUser user, TtsAudioFile audioFile, Integer speakerCount, Integer totalDurationSeconds) {
        String projectId = UUID.randomUUID().toString();
        String speechSegmentId = UUID.randomUUID().toString();
        String assetId = UUID.randomUUID().toString();
        String storageKey = storageKeyBuilder.projectAsset(user.id(), projectId, AudioAssetType.PREVIEW_MP3, 1, "mp3");

        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "Generated audiobook preview",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            1,
            speakerCount,
            totalDurationSeconds,
            now,
            now
        );
        project.setWorkflowStage(AudiobookWorkflowStage.AUDIO_GENERATED);
        project.setProductionPrompt("An immersive audiobook performance with a clear narrator and distinct character voices.");
        project.setProductionLanguageCode("en-US");
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project.setProductionAudioEncoding("MP3");
        projectRepository.save(project);

        AudiobookSpeechSegment speechSegment = new AudiobookSpeechSegment(
            speechSegmentId,
            project,
            0,
            "Preview speech segment",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            totalDurationSeconds,
            now,
            now,
            null,
            null,
            null,
            null
        );
        speechSegment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.GENERATED_PREVIEW);
        segmentRepository.save(speechSegment);

        AudioAsset asset = new AudioAsset(
            assetId,
            project,
            speechSegment,
            AudioAssetType.PREVIEW_MP3,
            1,
            storageKey,
            audioFile.filename(),
            audioFile.contentType(),
            audioFile.content().length,
            totalDurationSeconds,
            AudioAssetStatus.READY,
            now
        );
        assetRepository.save(asset);
    }

    private AudioAssetResponse assetResponse(AudioAsset asset) {
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
            "/api/audiobooks/" + asset.getProjectId() + "/audio-assets/" + asset.getId() + "/stream"
        );
    }
}


