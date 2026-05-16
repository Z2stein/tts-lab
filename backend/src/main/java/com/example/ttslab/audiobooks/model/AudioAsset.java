package com.example.ttslab.audiobooks.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audio_asset")
public class AudioAsset {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private AudiobookProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "speech_segment_id")
    private AudiobookSpeechSegment segment;

    // Stored for JDBC/legacy code that doesn't have full objects loaded
    private transient String projectIdValue;
    private transient String speechSegmentIdValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudioAssetType type;

    @Column(nullable = false)
    private int version;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudioAssetStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AudioAsset() {
    }

    // Constructor for JPA/new code with full objects
    public AudioAsset(
        String id,
        AudiobookProject project,
        AudiobookSpeechSegment segment,
        AudioAssetType type,
        int version,
        String storageKey,
        String filename,
        String contentType,
        long sizeBytes,
        Integer durationSeconds,
        AudioAssetStatus status,
        Instant createdAt
    ) {
        this.id = id;
        this.project = project;
        this.segment = segment;
        this.type = type;
        this.version = version;
        this.storageKey = storageKey;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = durationSeconds;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Constructor for JDBC/legacy code with ID strings
    public AudioAsset(
        String id,
        String projectId,
        String speechSegmentId,
        AudioAssetType type,
        int version,
        String storageKey,
        String filename,
        String contentType,
        long sizeBytes,
        Integer durationSeconds,
        AudioAssetStatus status,
        Instant createdAt
    ) {
        this.id = id;
        this.projectIdValue = projectId;
        this.speechSegmentIdValue = speechSegmentId;
        this.type = type;
        this.version = version;
        this.storageKey = storageKey;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.durationSeconds = durationSeconds;
        this.status = status;
        this.createdAt = createdAt;
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

    public AudiobookSpeechSegment getSegment() {
        return segment;
    }

    public void setSegment(AudiobookSpeechSegment segment) {
        this.segment = segment;
    }

    public String getSpeechSegmentId() {
        if (speechSegmentIdValue != null) {
            return speechSegmentIdValue;
        }
        return segment != null ? segment.getId() : null;
    }

    public AudioAssetType getType() {
        return type;
    }

    public void setType(AudioAssetType type) {
        this.type = type;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public AudioAssetStatus getStatus() {
        return status;
    }

    public void setStatus(AudioAssetStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
