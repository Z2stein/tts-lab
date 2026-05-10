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
            .map(project -> {
                var assets = repository.findAssetsForProject(project.id());
                int segmentCount = repository.findSegmentsForProject(project.id()).size();
                int characterCount = repository.findCharactersForProject(project.id()).size();

                return new AudiobookSummaryResponse.AudiobookSummaryItem(
                    project.id(),
                    project.title(),
                    project.status(),
                    segmentCount,
                    characterCount,
                    (int) assets.stream().mapToLong(a -> a.durationMs() != null ? a.durationMs() : 0).sum() / 1000,
                    project.updatedAt(),
                    assets.stream().map(this::assetResponse).toList()
                );
            })
            .toList();
        return new AudiobookSummaryResponse(items);
    }

    public AudiobookDetailResponse detail(CurrentUser user, String projectId) {
        AudiobookProject project = repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The requested audiobook was not found."));

        List<SpeechSegment> segments = repository.findSegmentsForProject(projectId);
        List<Character> characters = repository.findCharactersForProject(projectId);
        var characterMap = characters.stream().collect(java.util.stream.Collectors.toMap(Character::id, c -> c));

        return new AudiobookDetailResponse(
            project.id(),
            project.title(),
            project.status(),
            segments.size(),
            characters.size(),
            (int) repository.findAssetsForProject(projectId).stream().mapToLong(a -> a.durationMs() != null ? a.durationMs() : 0).sum() / 1000,
            project.createdAt(),
            project.updatedAt(),
            segments.stream()
                .map(segment -> {
                    Character character = characterMap.get(segment.characterId());
                    return new AudiobookSpeechSegmentResponse(
                        segment.id(),
                        segment.sequenceNo(),
                        character != null ? character.name() : "Unknown",
                        null, // Legacy review status
                        null,
                        character != null ? character.name() : null,
                        character != null ? character.roleDescription() : null,
                        character != null ? character.voiceKey() : null,
                        null // Legacy performance directions
                    );
                })
                .toList(),
            repository.findAssetsForProject(projectId).stream().map(this::assetResponse).toList()
        );
    }

    public AudioAsset assetForDownload(CurrentUser user, String projectId, String assetId) {
        AudioAsset asset = repository.findAssetForUser(projectId, assetId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIO_ASSET_NOT_FOUND", "The requested audio asset was not found."));
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
            "Generated content",
            "en-US",
            "text-to-speech",
            "mp3",
            AudiobookProjectStatus.DRAFT,
            1,
            Instant.now(),
            Instant.now()
        );
        repository.createProject(project);
        return project;
    }

    public AudiobookProject getProjectForUser(String projectId, CurrentUser user) {
        return repository.findProjectForUser(projectId, user.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUDIOBOOK_NOT_FOUND", "The audiobook project was not found."));
    }

    public AudioAsset persistAudioAsset(
        AudiobookProject project,
        TtsAudioFile audioFile,
        int sceneCount,
        int version,
        Integer speakerCount,
        Integer totalDurationSeconds,
        String speakerName,
        String speakerRoleDescription,
        String voiceKey,
        String performanceDirections
    ) {
        String assetId = UUID.randomUUID().toString();
        String characterId = UUID.randomUUID().toString();
        String segmentId = UUID.randomUUID().toString();

        // Create or update character
        var existingCharacter = repository.findCharactersForProject(project.id()).stream()
            .filter(c -> c.name().equals(speakerName))
            .findFirst();

        String charId;
        if (existingCharacter.isPresent()) {
            charId = existingCharacter.get().id();
        } else {
            Character character = new Character(
                characterId,
                project.id(),
                speakerName != null ? speakerName : "Speaker " + UUID.randomUUID().toString().substring(0, 8),
                speakerRoleDescription,
                voiceKey != null ? voiceKey : "default",
                repository.findCharactersForProject(project.id()).size() + 1,
                false,
                Instant.now(),
                Instant.now()
            );
            repository.createCharacter(character);
            charId = character.id();
        }

        // Create segment
        SpeechSegment segment = new SpeechSegment(
            segmentId,
            project.id(),
            charId,
            repository.findSegmentsForProject(project.id()).size() + 1,
            speakerName != null ? speakerName : "Generated segment",
            null,
            false,
            false,
            Instant.now(),
            Instant.now()
        );
        repository.createSegment(segment);

        // Store audio file
        String storageKey = storageKeyBuilder.projectAsset(project.userId(), project.id(), AudioAssetType.SEGMENT_AUDIO, 1, "mp3");
        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        // Create asset
        AudioAsset asset = new AudioAsset(
            assetId,
            project.id(),
            null, // No generation run for legacy integration
            AudioAssetType.SEGMENT_AUDIO,
            audioFile.filename(),
            storageKey,
            audioFile.contentType(),
            totalDurationSeconds != null ? (long) totalDurationSeconds * 1000 : null,
            audioFile.content().length,
            Instant.now()
        );
        repository.createAudioAsset(asset);
        return asset;
    }

    public void persistGeneratedPreview(CurrentUser user, TtsAudioFile audioFile, Integer durationMs) {
        String projectId = UUID.randomUUID().toString();
        String assetId = UUID.randomUUID().toString();
        String characterId = UUID.randomUUID().toString();
        String segmentId = UUID.randomUUID().toString();

        // Store audio file
        String storageKey = storageKeyBuilder.projectAsset(user.id(), projectId, AudioAssetType.VOICE_PREVIEW, 1, "mp3");
        try {
            fileStorageService.put(storageKey, audioFile.content(), audioFile.contentType());
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_ASSET_WRITE_FAILED", "The generated audio could not be saved.", null, ex);
        }

        // Create project
        AudiobookProject project = new AudiobookProject(
            projectId,
            user.id(),
            "Generated audiobook preview",
            "Preview",
            "en-US",
            "text-to-speech",
            "mp3",
            AudiobookProjectStatus.DRAFT,
            1,
            Instant.now(),
            Instant.now()
        );
        repository.createProject(project);

        // Create character
        Character character = new Character(
            characterId,
            projectId,
            "Preview Speaker",
            null,
            "default",
            1,
            false,
            Instant.now(),
            Instant.now()
        );
        repository.createCharacter(character);

        // Create segment
        SpeechSegment segment = new SpeechSegment(
            segmentId,
            projectId,
            characterId,
            1,
            "Preview segment",
            null,
            false,
            false,
            Instant.now(),
            Instant.now()
        );
        repository.createSegment(segment);

        // Create asset
        AudioAsset asset = new AudioAsset(
            assetId,
            projectId,
            null,
            AudioAssetType.VOICE_PREVIEW,
            audioFile.filename(),
            storageKey,
            audioFile.contentType(),
            (long) durationMs,
            audioFile.content().length,
            Instant.now()
        );
        repository.createAudioAsset(asset);
    }

    private AudioAssetResponse assetResponse(AudioAsset asset) {
        return new AudioAssetResponse(
            asset.id(),
            null, // sceneId no longer applicable
            asset.type(),
            1, // version no longer applicable, use default
            asset.fileName(),
            asset.contentType(),
            asset.sizeBytes(),
            asset.durationMs() != null ? asset.durationMs().intValue() / 1000 : null,
            null, // status no longer applicable, will cause null pointer if accessed
            asset.createdAt(),
            "/api/audiobooks/" + asset.projectId() + "/audio-assets/" + asset.id() + "/download",
            "/api/audiobooks/" + asset.projectId() + "/audio-assets/" + asset.id() + "/stream"
        );
    }
}
