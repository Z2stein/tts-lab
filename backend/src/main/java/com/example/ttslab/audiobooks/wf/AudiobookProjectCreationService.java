package com.example.ttslab.audiobooks.wf;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AudiobookProjectCreationService {

    private final AudiobookProjectRepository audiobookProjectRepository;

    public AudiobookProjectCreationService(AudiobookProjectRepository audiobookProjectRepository) {
        this.audiobookProjectRepository = audiobookProjectRepository;
    }

    public AudiobookProject createProject(ProjectCreationRequest request) {
        String projectId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String title = generateProjectTitle(request.rawDialogue());

        AudiobookProject project = new AudiobookProject(
                projectId,
                request.userId(),
                title,
                AudiobookProjectStatus.DRAFT,
                "voice_analysis",
                0,
                request.speakerCount(),
                null,
                now,
                now
        );

        audiobookProjectRepository.save(project);
        return project;
    }

    private String generateProjectTitle(String rawDialogue) {
        int maxLength = 255;
        String prefix = "Analysis: ";
        String truncated = rawDialogue.substring(0, Math.min(rawDialogue.length(), maxLength - prefix.length()));
        return prefix + truncated.replace("\n", " ").replace("\r", "");
    }
}
