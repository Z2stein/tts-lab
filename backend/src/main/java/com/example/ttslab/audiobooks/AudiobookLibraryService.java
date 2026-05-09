package com.example.ttslab.audiobooks;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.projects.ttsworkbench.TtsAudioFile;
import com.example.ttslab.storage.FileStorageService;
import com.example.ttslab.storage.StorageKeyBuilder;
import com.example.ttslab.storage.StoredFile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AudiobookLibraryService {
    private final AudiobookRepository repository;
    private final FileStorageService fileStorageService;
    private final StorageKeyBuilder storageKeyBuilder;

    public AudiobookLibraryService(
        AudiobookRepository repository,
        FileStorageService fileStorageService,
        StorageKeyBuilder storageKeyBuilder
    ) {
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.storageKeyBuilder = storageKeyBuilder;
    }

    public AudiobookSummaryResponse list(CurrentUser user) {
        List<AudiobookSummaryResponse.AudiobookSummaryItem> items = repository.findProjectsForUser(user.id()).stream()
            .map(project -> new AudiobookSummaryResponse.AudiobookSummaryItem(
                project.id(),
                project.title(),
                project.status(),
                project.sceneCount(),
                project.speakerCount(),
                project.totalDurationSeconds(),
                project.updatedAt(),
                repository.findAssets(project.id()).stream().map(this::assetResponse).toList()
            ))
            .toList();
        return new AudiobookSummaryResponse(items);
    }

    public AudiobookDetailResponse detail(CurrentUser user, String projectId) {
        AudiobookProject project = repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));
        return new AudiobookDetailResponse(
            project.id(),
            project.title(),
            project.status(),
            project.sceneCount(),
            project.speakerCount(),
            project.totalDurationSeconds(),
            project.createdAt(),
            project.updatedAt(),
            repository.findScenes(project.id()).stream()
                .map(scene -> new AudiobookSceneResponse(scene.id(), scene.orderIndex(), scene.title(), scene.reviewStatus(), scene.durationSeconds()))
                .toList(),
            repository.findAssets(project.id()).stream().map(this::assetResponse).toList()
        );
    }

    public AudioAsset assetForDownload(CurrentUser user, String projectId, String assetId) {
        AudioAsset asset = repository.findAssetForUser(projectId, assetId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIO_ASSET_NOT_FOUND", "The requested audio asset was not found."));
        if (asset.status() != AudioAssetStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "AUDIO_ASSET_NOT_READY", "The requested audio asset is not ready yet.");
        }
        return asset;
    }

    public StoredFile read(AudioAsset asset) {
        try {
            return fileStorageService.get(asset.storageKey(), asset.contentType(), asset.sizeBytes());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_READ_FAILED", "The audio file could not be read.", null, ex);
        }
    }

    public AudiobookProject createProjectForGeneration(CurrentUser user) {
        String projectId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "Generated audiobook " + timestamp,
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            0,
            null,
            null,
            null,
            null
        );
        repository.createProject(project);
        return project;
    }

    public AudiobookProject getProjectForUser(String projectId, CurrentUser user) {
        return repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The audiobook project was not found."));
    }

    public AudioAsset persistAudioAsset(AudiobookProject project, TtsAudioFile audioFile, int version, Integer speakerCount, Integer totalDurationSeconds) {
        String assetId = UUID.randomUUID().toString();
        String sceneId = UUID.randomUUID().toString();
        String storageKey = storageKeyBuilder.projectAsset(project.userId(), project.id(), AudioAssetType.PREVIEW_MP3, version, "mp3");

        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        repository.updateProjectMetadata(project.id(), speakerCount, totalDurationSeconds);

        AudiobookScene scene = new AudiobookScene(
            sceneId,
            project.id(),
            0,
            "Generated scene",
            AudiobookSceneReviewStatus.PENDING,
            null,
            null,
            null
        );
        AudioAsset asset = new AudioAsset(
            assetId,
            project.id(),
            sceneId,
            AudioAssetType.PREVIEW_MP3,
            version,
            storageKey,
            audioFile.filename(),
            audioFile.contentType(),
            audioFile.content().length,
            totalDurationSeconds,
            AudioAssetStatus.READY,
            null
        );
        repository.addScene(scene);
        repository.addAsset(asset);
        return asset;
    }

    public void persistGeneratedPreview(CurrentUser user, TtsAudioFile audioFile, Integer speakerCount, Integer totalDurationSeconds) {
        String projectId = UUID.randomUUID().toString();
        String sceneId = UUID.randomUUID().toString();
        String assetId = UUID.randomUUID().toString();
        String storageKey = storageKeyBuilder.projectAsset(user.id(), projectId, AudioAssetType.PREVIEW_MP3, 1, "mp3");

        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "Generated audiobook preview",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH",
            1,
            speakerCount,
            totalDurationSeconds,
            null,
            null
        );
        AudiobookScene scene = new AudiobookScene(
            sceneId,
            projectId,
            0,
            "Preview scene",
            AudiobookSceneReviewStatus.PENDING,
            null,
            null,
            null
        );
        AudioAsset asset = new AudioAsset(
            assetId,
            projectId,
            sceneId,
            AudioAssetType.PREVIEW_MP3,
            1,
            storageKey,
            audioFile.filename(),
            audioFile.contentType(),
            audioFile.content().length,
            totalDurationSeconds,
            AudioAssetStatus.READY,
            null
        );
        repository.createProjectWithAsset(project, scene, asset);
    }

    private AudioAssetResponse assetResponse(AudioAsset asset) {
        return new AudioAssetResponse(
            asset.id(),
            asset.sceneId(),
            asset.type(),
            asset.version(),
            asset.filename(),
            asset.contentType(),
            asset.sizeBytes(),
            asset.durationSeconds(),
            asset.status(),
            asset.createdAt(),
            "/api/audiobooks/" + asset.projectId() + "/audio-assets/" + asset.id() + "/download",
            "/api/audiobooks/" + asset.projectId() + "/audio-assets/" + asset.id() + "/stream"
        );
    }
}
