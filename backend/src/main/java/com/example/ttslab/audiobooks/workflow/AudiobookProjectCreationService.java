package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AudiobookProjectCreationService {

    private final AudiobookProjectRepository audiobookProjectRepository;

    public AudiobookProjectCreationService(AudiobookProjectRepository audiobookProjectRepository) {
        this.audiobookProjectRepository = audiobookProjectRepository;
    }

    public AudiobookProject createProject(String userId) {
        return createProject(userId, "Untitled audiobook", null, "en-US");
    }

    public AudiobookProject createProject(String userId, String title) {
        return createProject(userId, title, null, "en-US");
    }

    public AudiobookProject createProject(String userId, String title, String storyText) {
        return createProject(userId, title, storyText, "en-US");
    }

    public AudiobookProject createProject(String userId, String title, String storyText, String languageCode) {
        String projectId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String safeTitle = title == null || title.isBlank() ? "Untitled audiobook" : title.trim();

        AudiobookProject project = new AudiobookProject(
                projectId,
                userId,
                safeTitle,
                AudiobookProjectStatus.DRAFT,
                "voice_analysis",
                0,
                null,
                null,
                now,
                now
        );
        project.setStoryText(storyText);
        project.setWorkflowStage(AudiobookWorkflowStage.CAST_REVIEW);
        project.setAudioAssetsCurrent(false);
        project.setProductionPrompt("An immersive audiobook performance with a clear narrator and distinct character voices.");
        project.setProductionLanguageCode(languageCode == null || languageCode.isBlank() ? "en-US" : languageCode.trim());
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project.setProductionAudioEncoding("MP3");

        audiobookProjectRepository.save(project);
        return project;
    }
}

