package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.error.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudiobookProjectCreationService {

    private final AudiobookProjectRepository audiobookProjectRepository;
    private final SpeakerCharacterRepository speakerCharacterRepository;

    public AudiobookProjectCreationService(
        AudiobookProjectRepository audiobookProjectRepository,
        SpeakerCharacterRepository speakerCharacterRepository
    ) {
        this.audiobookProjectRepository = audiobookProjectRepository;
        this.speakerCharacterRepository = speakerCharacterRepository;
    }

    public AudiobookProject createProject(String userId) {
        return createProject(userId, "Untitled audiobook", null, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE);
    }

    public AudiobookProject createProject(String userId, String title) {
        return createProject(userId, title, null, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE);
    }

    public AudiobookProject createProject(String userId, String title, String storyText) {
        return createProject(userId, title, storyText, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE, SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE);
    }

    public AudiobookProject createProject(String userId, String title, String storyText, String languageCode) {
        return createProject(userId, title, storyText, languageCode, languageCode);
    }

    @Transactional
    public AudiobookProject createProjectWithSpeakers(
        String userId,
        String title,
        String storyText,
        String sourceLanguageCode,
        String productionLanguageCode,
        List<SpeakerVoiceAnalysisItem> speakers
    ) {
        AudiobookProject project = createProject(userId, title, storyText, sourceLanguageCode, productionLanguageCode);
        persistSpeakerCharacters(project.getId(), speakers);
        return project;
    }

    public AudiobookProject createProject(String userId, String title, String storyText, String sourceLanguageCode, String productionLanguageCode) {
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
        project.setSourceLanguageCode(sourceLanguageCode == null || sourceLanguageCode.isBlank()
            ? SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE
            : sourceLanguageCode.trim());
        project.setProductionLanguageCode(productionLanguageCode == null || productionLanguageCode.isBlank()
            ? SupportedLanguageCodes.DEFAULT_PRODUCTION_LANGUAGE_CODE
            : productionLanguageCode.trim());
        project.setProductionModelName("gemini-3.1-flash-tts-preview");
        project.setProductionAudioEncoding("MP3");

        audiobookProjectRepository.save(project);
        return project;
    }

    private void persistSpeakerCharacters(String projectId, List<SpeakerVoiceAnalysisItem> speakers) {
        List<SpeakerVoiceAnalysisItem> safeSpeakers = speakers == null ? List.of() : speakers;

        AudiobookProject project = audiobookProjectRepository.findById(projectId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "AUDIOBOOK_NOT_FOUND",
                "The audiobook project was not found."
            ));

        speakerCharacterRepository.deleteByProjectId(projectId);

        Instant now = Instant.now();
        List<SpeakerCharacter> characters = new ArrayList<>();
        for (int index = 0; index < safeSpeakers.size(); index++) {
            SpeakerVoiceAnalysisItem speaker = safeSpeakers.get(index);
            characters.add(new SpeakerCharacter(
                UUID.randomUUID().toString(),
                projectId,
                index,
                speaker.speakerName(),
                speaker.roleDescription(),
                speaker.voiceSuggestion(),
                now
            ));
        }

        if (!characters.isEmpty()) {
            speakerCharacterRepository.saveAll(characters);
        }

        project.setSpeakerCount(safeSpeakers.size());
        project.setUpdatedAt(now);
        audiobookProjectRepository.save(project);
    }
}

