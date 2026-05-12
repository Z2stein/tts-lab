package com.example.ttslab.audiobooks.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "audiobook_project")
public class AudiobookProject {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudiobookProjectStatus status;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "speech_segment_count", nullable = false)
    private int speechSegmentCount;

    @Column(name = "speaker_count")
    private Integer speakerCount;

    @Column(name = "total_duration_seconds")
    private Integer totalDurationSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AudiobookSpeechSegment> speechSegments = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AudioAsset> assets = new ArrayList<>();

    public AudiobookProject() {
    }

    public AudiobookProject(
        String id,
        String userId,
        String title,
        AudiobookProjectStatus status,
        String sourceType,
        int speechSegmentCount,
        Integer speakerCount,
        Integer totalDurationSeconds,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.status = status;
        this.sourceType = sourceType;
        this.speechSegmentCount = speechSegmentCount;
        this.speakerCount = speakerCount;
        this.totalDurationSeconds = totalDurationSeconds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AudiobookProjectStatus getStatus() {
        return status;
    }

    public void setStatus(AudiobookProjectStatus status) {
        this.status = status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public int getSpeechSegmentCount() {
        return speechSegmentCount;
    }

    public void setSpeechSegmentCount(int speechSegmentCount) {
        this.speechSegmentCount = speechSegmentCount;
    }

    public Integer getSpeakerCount() {
        return speakerCount;
    }

    public void setSpeakerCount(Integer speakerCount) {
        this.speakerCount = speakerCount;
    }

    public Integer getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public void setTotalDurationSeconds(Integer totalDurationSeconds) {
        this.totalDurationSeconds = totalDurationSeconds;
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

    public List<AudiobookSpeechSegment> getSpeechSegments() {
        return speechSegments;
    }

    public void setSpeechSegments(List<AudiobookSpeechSegment> speechSegments) {
        this.speechSegments = speechSegments;
    }

    public List<AudioAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<AudioAsset> assets) {
        this.assets = assets;
    }
}
