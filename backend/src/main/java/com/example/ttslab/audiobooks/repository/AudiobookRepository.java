package com.example.ttslab.audiobooks.repository;

import com.example.ttslab.audiobooks.model.AudioAsset;
import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AudiobookRepository {
    private final AudiobookProjectRepository projectRepository;
    private final AudiobookSpeechSegmentRepository segmentRepository;
    private final AudioAssetRepository assetRepository;

    public AudiobookRepository(
        AudiobookProjectRepository projectRepository,
        AudiobookSpeechSegmentRepository segmentRepository,
        AudioAssetRepository assetRepository
    ) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
        this.assetRepository = assetRepository;
    }

    public List<AudiobookProject> findProjectsForUser(String userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId);
    }

    public Optional<AudiobookProject> findProjectForUser(String projectId, String userId) {
        return projectRepository.findByIdAndUserId(projectId, userId);
    }

    public List<AudiobookSpeechSegment> findSpeechSegments(String projectId) {
        return findPreviewSpeechSegments(projectId);
    }

    public List<AudiobookSpeechSegment> findPreviewSpeechSegments(String projectId) {
        return segmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(projectId, AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
    }

    public List<AudioAsset> findAssets(String projectId) {
        return assetRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public Optional<AudioAsset> findAssetForUser(String projectId, String assetId, String userId) {
        return assetRepository.findByIdAndProjectIdAndProjectUserId(assetId, projectId, userId);
    }

    public void createProject(AudiobookProject project) {
        if (project.getCreatedAt() == null) {
            Instant now = Instant.now();
            project.setCreatedAt(now);
            project.setUpdatedAt(now);
        } else if (project.getUpdatedAt() == null) {
            project.setUpdatedAt(project.getCreatedAt());
        }
        projectRepository.save(project);
    }

    @Transactional
    public void updateProjectMetadata(String projectId, Integer speechSegmentCount, Integer speakerCount, Integer totalDurationSeconds) {
        projectRepository.findById(projectId).ifPresent(project -> {
            project.setSpeechSegmentCount(speechSegmentCount == null ? 0 : speechSegmentCount);
            project.setSpeakerCount(speakerCount);
            project.setTotalDurationSeconds(totalDurationSeconds);
        });
    }

    public void addSpeechSegment(AudiobookSpeechSegment speechSegment) {
        if (speechSegment.getProject() == null && speechSegment.getProjectId() != null) {
            speechSegment.setProject(projectRepository.getReferenceById(speechSegment.getProjectId()));
        }
        if (speechSegment.getCreatedAt() == null) {
            Instant now = Instant.now();
            speechSegment.setCreatedAt(now);
            speechSegment.setUpdatedAt(now);
        } else if (speechSegment.getUpdatedAt() == null) {
            speechSegment.setUpdatedAt(speechSegment.getCreatedAt());
        }
        segmentRepository.save(speechSegment);
    }

    public void addAsset(AudioAsset asset) {
        if (asset.getProject() == null && asset.getProjectId() != null) {
            asset.setProject(projectRepository.getReferenceById(asset.getProjectId()));
        }
        if (asset.getSegment() == null && asset.getSpeechSegmentId() != null) {
            asset.setSegment(segmentRepository.getReferenceById(asset.getSpeechSegmentId()));
        }
        if (asset.getCreatedAt() == null) {
            asset.setCreatedAt(Instant.now());
        }
        assetRepository.save(asset);
    }

    @Transactional
    public void createProjectWithAsset(AudiobookProject project, AudiobookSpeechSegment speechSegment, AudioAsset asset) {
        createProject(project);
        addSpeechSegment(speechSegment);
        addAsset(asset);
    }
}
