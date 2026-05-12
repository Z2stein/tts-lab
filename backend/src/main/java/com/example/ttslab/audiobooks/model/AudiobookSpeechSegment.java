package com.example.ttslab.audiobooks.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "audiobook_speech_segment")
public class AudiobookSpeechSegment {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private AudiobookProject project;

    // Stored for JDBC/legacy code that doesn't have the full project object loaded
    private transient String projectIdValue;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private AudiobookSpeechSegmentReviewStatus reviewStatus;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "speaker_name")
    private String speakerName;

    @Column(name = "speaker_role_description")
    private String speakerRoleDescription;

    @Column(name = "voice_name")
    private String voiceName;

    @Column(name = "performance_directions")
    private String performanceDirections;

    @Column(name = "original_text")
    private String originalText;

    @Column(name = "character_id")
    private String characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_origin", nullable = false)
    private AudiobookSpeechSegmentOrigin segmentOrigin = AudiobookSpeechSegmentOrigin.LEGACY;

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AudioAsset> assets = new ArrayList<>();

    public AudiobookSpeechSegment() {
    }

    // Constructor for JPA/new code with full project object
    public AudiobookSpeechSegment(
        String id,
        AudiobookProject project,
        int orderIndex,
        String title,
        AudiobookSpeechSegmentReviewStatus reviewStatus,
        Integer durationSeconds,
        Instant createdAt,
        Instant updatedAt,
        String speakerName,
        String speakerRoleDescription,
        String voiceName,
        String performanceDirections
    ) {
        this(id, project, orderIndex, title, reviewStatus, durationSeconds, createdAt, updatedAt, speakerName, speakerRoleDescription, voiceName, performanceDirections, null, null);
    }

    public AudiobookSpeechSegment(
        String id,
        AudiobookProject project,
        int orderIndex,
        String title,
        AudiobookSpeechSegmentReviewStatus reviewStatus,
        Integer durationSeconds,
        Instant createdAt,
        Instant updatedAt,
        String speakerName,
        String speakerRoleDescription,
        String voiceName,
        String performanceDirections,
        String originalText,
        String characterId
    ) {
        this.id = id;
        this.project = project;
        this.orderIndex = orderIndex;
        this.title = title;
        this.reviewStatus = reviewStatus;
        this.durationSeconds = durationSeconds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.speakerName = speakerName;
        this.speakerRoleDescription = speakerRoleDescription;
        this.voiceName = voiceName;
        this.performanceDirections = performanceDirections;
        this.originalText = originalText;
        this.characterId = characterId;
    }

    // Constructor for JDBC/legacy code with projectId string
    public AudiobookSpeechSegment(
        String id,
        String projectId,
        int orderIndex,
        String title,
        AudiobookSpeechSegmentReviewStatus reviewStatus,
        Integer durationSeconds,
        Instant createdAt,
        Instant updatedAt,
        String speakerName,
        String speakerRoleDescription,
        String voiceName,
        String performanceDirections
    ) {
        this(id, projectId, orderIndex, title, reviewStatus, durationSeconds, createdAt, updatedAt, speakerName, speakerRoleDescription, voiceName, performanceDirections, null, null);
    }

    public AudiobookSpeechSegment(
        String id,
        String projectId,
        int orderIndex,
        String title,
        AudiobookSpeechSegmentReviewStatus reviewStatus,
        Integer durationSeconds,
        Instant createdAt,
        Instant updatedAt,
        String speakerName,
        String speakerRoleDescription,
        String voiceName,
        String performanceDirections,
        String originalText,
        String characterId
    ) {
        this.id = id;
        this.projectIdValue = projectId;
        this.orderIndex = orderIndex;
        this.title = title;
        this.reviewStatus = reviewStatus;
        this.durationSeconds = durationSeconds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.speakerName = speakerName;
        this.speakerRoleDescription = speakerRoleDescription;
        this.voiceName = voiceName;
        this.performanceDirections = performanceDirections;
        this.originalText = originalText;
        this.characterId = characterId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AudiobookProject getProject() {
        return project;
    }

    public void setProject(AudiobookProject project) {
        this.project = project;
    }

    public String getProjectId() {
        if (projectIdValue != null) {
            return projectIdValue;
        }
        return project != null ? project.getId() : null;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AudiobookSpeechSegmentReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(AudiobookSpeechSegmentReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public String getSpeakerRoleDescription() {
        return speakerRoleDescription;
    }

    public void setSpeakerRoleDescription(String speakerRoleDescription) {
        this.speakerRoleDescription = speakerRoleDescription;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public String getPerformanceDirections() {
        return performanceDirections;
    }

    public void setPerformanceDirections(String performanceDirections) {
        this.performanceDirections = performanceDirections;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getCharacterId() {
        return characterId;
    }

    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }

    public AudiobookSpeechSegmentOrigin getSegmentOrigin() {
        return segmentOrigin;
    }

    public void setSegmentOrigin(AudiobookSpeechSegmentOrigin segmentOrigin) {
        this.segmentOrigin = segmentOrigin == null ? AudiobookSpeechSegmentOrigin.LEGACY : segmentOrigin;
    }

    public List<AudioAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<AudioAsset> assets) {
        this.assets = assets;
    }
}
