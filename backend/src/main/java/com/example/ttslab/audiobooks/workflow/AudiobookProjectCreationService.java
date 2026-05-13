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
        String projectId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AudiobookProject project = new AudiobookProject(
                projectId,
                userId,
                "Speaker analysis",
                AudiobookProjectStatus.DRAFT,
                "voice_analysis",
                0,
                null,
                null,
                now,
                now
        );

        audiobookProjectRepository.save(project);
        return project;
    }
}

