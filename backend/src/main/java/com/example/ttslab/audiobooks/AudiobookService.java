package com.example.ttslab.audiobooks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudiobookService {
    private final AudiobookRepository repository;

    public AudiobookService(AudiobookRepository repository) {
        this.repository = repository;
    }

    // ==================== Project Operations ====================

    @Transactional
    public AudiobookProject createAudiobookProject(
        String userId,
        String title,
        String sourceText,
        String languageCode,
        String modelName,
        String audioEncoding
    ) {
        AudiobookProject project = new AudiobookProject(
            UUID.randomUUID().toString(),
            userId,
            title,
            sourceText,
            languageCode,
            modelName,
            audioEncoding,
            AudiobookProjectStatus.DRAFT,
            1,
            Instant.now(),
            Instant.now()
        );
        repository.createProject(project);
        return project;
    }

    public AudiobookProject getProjectForUser(String projectId, String userId) {
        return repository.findProjectForUser(projectId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    public List<AudiobookProject> listProjectsForUser(String userId) {
        return repository.findProjectsForUser(userId);
    }

    public void updateProjectStatus(String projectId, AudiobookProjectStatus status) {
        var project = repository.findProjectForUser(projectId, "").stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        AudiobookProject updated = new AudiobookProject(
            project.id(),
            project.userId(),
            project.title(),
            project.sourceText(),
            project.languageCode(),
            project.modelName(),
            project.audioEncoding(),
            status,
            project.revision() + 1,
            project.createdAt(),
            Instant.now()
        );
        repository.updateProject(updated);
    }

    // ==================== Character Operations ====================

    @Transactional
    public Character castCharacter(
        String projectId,
        String name,
        String roleDescription,
        String voiceKey,
        int sortOrder
    ) {
        Character character = new Character(
            UUID.randomUUID().toString(),
            projectId,
            name,
            roleDescription,
            voiceKey,
            sortOrder,
            false,
            Instant.now(),
            Instant.now()
        );
        repository.createCharacter(character);
        return character;
    }

    public List<Character> getCharactersForProject(String projectId) {
        return repository.findCharactersForProject(projectId);
    }

    public Character getCharacter(String characterId, String projectId) {
        return repository.findCharacter(characterId, projectId)
            .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));
    }

    public void updateCharacter(Character character) {
        repository.updateCharacter(character);
    }

    public void removeCharacter(String characterId) {
        repository.deleteCharacter(characterId);
    }

    // ==================== Segment Operations ====================

    @Transactional
    public SpeechSegment addSegment(
        String projectId,
        String characterId,
        int sequenceNo,
        String originalText
    ) {
        SpeechSegment segment = new SpeechSegment(
            UUID.randomUUID().toString(),
            projectId,
            characterId,
            sequenceNo,
            originalText,
            null,
            false,
            false,
            Instant.now(),
            Instant.now()
        );
        repository.createSegment(segment);
        return segment;
    }

    public List<SpeechSegment> getSegmentsForProject(String projectId) {
        return repository.findSegmentsForProject(projectId);
    }

    public SpeechSegment getSegment(String segmentId, String projectId) {
        return repository.findSegment(segmentId, projectId)
            .orElseThrow(() -> new IllegalArgumentException("Segment not found: " + segmentId));
    }

    public void updateSegment(SpeechSegment segment) {
        repository.updateSegment(segment);
    }

    public void removeSegment(String segmentId) {
        repository.deleteSegment(segmentId);
    }

    // ==================== Generation Run Operations ====================

    @Transactional
    public AIGenerationRun startGenerationRun(String projectId, RunType runType, String requestJson) {
        AIGenerationRun run = new AIGenerationRun(
            UUID.randomUUID().toString(),
            projectId,
            runType,
            RunStatus.PENDING,
            requestJson,
            null,
            null,
            Instant.now(),
            null,
            Instant.now()
        );
        repository.createGenerationRun(run);
        return run;
    }

    public AIGenerationRun getGenerationRun(String runId, String projectId) {
        return repository.findGenerationRun(runId, projectId)
            .orElseThrow(() -> new IllegalArgumentException("Generation run not found: " + runId));
    }

    public List<AIGenerationRun> getGenerationRunsForProject(String projectId) {
        return repository.findGenerationRunsForProject(projectId);
    }

    @Transactional
    public void completeGenerationRun(String runId, String responseJson) {
        repository.updateGenerationRunStatus(runId, RunStatus.SUCCEEDED, responseJson, null);
    }

    @Transactional
    public void failGenerationRun(String runId, String errorMessage) {
        repository.updateGenerationRunStatus(runId, RunStatus.FAILED, null, errorMessage);
    }

    // ==================== Render Operations ====================

    @Transactional
    public RenderSegment logRenderAttempt(
        String runId,
        String segmentId,
        String text,
        String providerRequestJson
    ) {
        RenderSegment render = new RenderSegment(
            UUID.randomUUID().toString(),
            runId,
            segmentId,
            text,
            providerRequestJson,
            RunStatus.PENDING,
            null,
            Instant.now(),
            Instant.now(),
            null
        );
        repository.createRenderSegment(render);
        return render;
    }

    public RenderSegment getRenderSegment(String renderId) {
        return repository.findRenderSegment(renderId)
            .orElseThrow(() -> new IllegalArgumentException("Render segment not found: " + renderId));
    }

    public List<RenderSegment> getRenderSegmentsForRun(String runId) {
        return repository.findRenderSegmentsForRun(runId);
    }

    @Transactional
    public void completeRenderSegment(String renderId, String audioAssetId) {
        repository.updateRenderSegmentStatus(renderId, RunStatus.SUCCEEDED, audioAssetId);
    }

    @Transactional
    public void failRenderSegment(String renderId) {
        repository.updateRenderSegmentStatus(renderId, RunStatus.FAILED, null);
    }

    // ==================== Asset Operations ====================

    @Transactional
    public AudioAsset createAudioAsset(
        String projectId,
        String aiGenerationRunId,
        AudioAssetType type,
        String fileName,
        String storageKey,
        String contentType,
        Long durationMs,
        long sizeBytes
    ) {
        AudioAsset asset = new AudioAsset(
            UUID.randomUUID().toString(),
            projectId,
            aiGenerationRunId,
            type,
            fileName,
            storageKey,
            contentType,
            durationMs,
            sizeBytes,
            Instant.now()
        );
        repository.createAudioAsset(asset);
        return asset;
    }

    public List<AudioAsset> getAssetsForProject(String projectId) {
        return repository.findAssetsForProject(projectId);
    }

    public AudioAsset getAssetForUser(String projectId, String assetId, String userId) {
        return repository.findAssetForUser(projectId, assetId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
    }

    // ==================== Transactional Bulk Operations ====================

    @Transactional
    public void createProjectWithCharactersAndSegments(
        AudiobookProject project,
        List<Character> characters,
        List<SpeechSegment> segments
    ) {
        repository.createProjectWithCharactersAndSegments(project, characters, segments);
    }

    @Transactional
    public void createGenerationRunWithRenders(
        AIGenerationRun run,
        List<RenderSegment> renders,
        List<AudioAsset> assets
    ) {
        repository.createGenerationRunWithRenders(run, renders, assets);
    }
}
