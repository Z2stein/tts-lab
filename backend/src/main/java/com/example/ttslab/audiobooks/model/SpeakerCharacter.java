package com.example.ttslab.audiobooks.model;

import com.example.ttslab.projects.ttsworkbench.SpeakerVoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "charakters")
public class SpeakerCharacter {
    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "speaker_name", nullable = false)
    private String speakerName;

    @Column(name = "role_description")
    private String roleDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice_suggestion", nullable = false)
    private SpeakerVoice voiceSuggestion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SpeakerCharacter() {
    }

    public SpeakerCharacter(
        String id,
        String projectId,
        int sortOrder,
        String speakerName,
        String roleDescription,
        SpeakerVoice voiceSuggestion,
        Instant createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.sortOrder = sortOrder;
        this.speakerName = speakerName;
        this.roleDescription = roleDescription;
        this.voiceSuggestion = voiceSuggestion;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public String getRoleDescription() {
        return roleDescription;
    }

    public void setRoleDescription(String roleDescription) {
        this.roleDescription = roleDescription;
    }

    public SpeakerVoice getVoiceSuggestion() {
        return voiceSuggestion;
    }

    public void setVoiceSuggestion(SpeakerVoice voiceSuggestion) {
        this.voiceSuggestion = voiceSuggestion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
