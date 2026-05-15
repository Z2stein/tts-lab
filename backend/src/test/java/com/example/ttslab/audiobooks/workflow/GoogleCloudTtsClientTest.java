package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCloudTtsClientTest {
    @Test
    void missingBase64CredentialsFailBeforeProviderCall() {
        GoogleCloudTtsClient client = new GoogleCloudTtsClient(" ");

        TtsAudioCreationException exception = assertThrows(TtsAudioCreationException.class, () -> client.synthesize(mockProject(), 0));

        assertTrue(exception.configurationError());
    }

    @Test
    void invalidBase64CredentialsFailBeforeProviderCall() {
        GoogleCloudTtsClient client = new GoogleCloudTtsClient("not base64!");

        TtsAudioCreationException exception = assertThrows(TtsAudioCreationException.class, () -> client.synthesize(mockProject(), 0));

        assertTrue(exception.configurationError());
    }

    private AudiobookProject mockProject() {
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            "test-project",
            "user-1",
            "Test Project",
            AudiobookProjectStatus.DRAFT,
            "text",
            1,
            1,
            60,
            now,
            now
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "char-1",
            "test-project",
            0,
            "Narrator",
            "Main narrator",
            SpeakerVoice.KORE,
            now
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            30,
            now,
            now,
            "Hello",
            "Hello",
            character
        );
        project.getSpeechSegments().add(segment);
        return project;
    }
}

