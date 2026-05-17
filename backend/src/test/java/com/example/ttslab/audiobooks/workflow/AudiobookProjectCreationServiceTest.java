package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AudiobookProjectCreationServiceTest {
    @Test
    void createProjectPersistsSourceAndProductionLanguageCode() {
        AudiobookProjectRepository projectRepository = mock(AudiobookProjectRepository.class);
        SpeakerCharacterRepository speakerCharacterRepository = mock(SpeakerCharacterRepository.class);
        AudiobookProjectCreationService service = new AudiobookProjectCreationService(projectRepository, speakerCharacterRepository);

        service.createProject("user-1", "German Story", "Hallo zusammen", "de-DE", "de-DE");

        var projectCaptor = forClass(AudiobookProject.class);
        verify(projectRepository).save(projectCaptor.capture());
        assertThat(projectCaptor.getValue().getProductionLanguageCode()).isEqualTo("de-DE");
        assertThat(projectCaptor.getValue().getSourceLanguageCode()).isEqualTo("de-DE");
    }
}
