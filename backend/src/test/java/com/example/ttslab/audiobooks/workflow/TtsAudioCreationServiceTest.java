package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookProjectStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.example.ttslab.audiobooks.workflow.service.TtsAudioCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsAudioCreationServiceTest {
    @Test
    void mockProviderReturnsDeterministicMp3WithoutCallingGoogleTts() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "mock");
        AudiobookProject project = mockProjectWithSegment("Hello");

        TtsAudioFile audioFile = service.createAudio(project, 0);

        assertEquals("audio/mpeg", audioFile.contentType());
        assertEquals("tts-render-plan.mp3", audioFile.filename());
        assertArrayEquals(new byte[] {'I', 'D', '3'}, new byte[] {audioFile.content()[0], audioFile.content()[1], audioFile.content()[2]});
        assertTrue(new String(audioFile.content(), StandardCharsets.UTF_8).contains("TTS-LAB-MOCK-MP3"));
        assertTrue(new String(audioFile.content(), StandardCharsets.UTF_8).contains("Hello"));
        verify(googleTtsClient, never()).synthesize(any(AudiobookProject.class), anyInt());
    }

    @Test
    void geminiProviderCallsGoogleTtsClientForSingleRequest() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        byte[] mp3 = new byte[] {'I', 'D', '3', 1};
        when(googleTtsClient.synthesize(any(AudiobookProject.class), anyInt())).thenReturn(mp3);
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "gemini");
        AudiobookProject project = mockProjectWithSegment("Real audio");

        TtsAudioFile audioFile = service.createAudio(project, 0);

        assertEquals("audio/mpeg", audioFile.contentType());
        assertArrayEquals(mp3, audioFile.content());
        verify(googleTtsClient).synthesize(project, 0);
    }

    @Test
    void inputTooLargeIsMappedToValidationErrorNotProviderOutage() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        when(googleTtsClient.synthesize(any(AudiobookProject.class), anyInt()))
            .thenThrow(new TtsAudioCreationException("too large", null, TtsAudioCreationException.Kind.INPUT_TOO_LARGE));
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "gemini");
        AudiobookProject project = mockProjectWithSegment("Very long text");

        ApiException exception = assertThrows(ApiException.class, () -> service.createAudio(project, 0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("TTS_INPUT_TOO_LARGE", exception.code());
    }

    @Test
    void providerFailureIsStillMappedToProviderUnavailable() {
        GoogleTtsClient googleTtsClient = mock(GoogleTtsClient.class);
        when(googleTtsClient.synthesize(any(AudiobookProject.class), anyInt()))
            .thenThrow(new TtsAudioCreationException("boom", null, TtsAudioCreationException.Kind.PROVIDER));
        TtsAudioCreationService service = new TtsAudioCreationService(provider(googleTtsClient), "gemini");
        AudiobookProject project = mockProjectWithSegment("Some text");

        ApiException exception = assertThrows(ApiException.class, () -> service.createAudio(project, 0));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.status());
        assertEquals("TTS_AUDIO_PROVIDER_UNAVAILABLE", exception.code());
    }

    @Test
    void multipleSegmentsReturnMockAudioForSpecificSegment() {
        TtsAudioCreationService service = new TtsAudioCreationService(provider(null), "mock");
        AudiobookProject project = mockProjectWithSegments("First", "Second");

        TtsAudioFile audioFile = service.createAudio(project, 0);

        assertEquals("audio/mpeg", audioFile.contentType());
        assertEquals("tts-render-plan.mp3", audioFile.filename());
        String content = new String(audioFile.content(), StandardCharsets.UTF_8);
        assertTrue(content.contains("First"));
        assertTrue(content.contains("TTS-LAB-MOCK-MP3"));
    }

    @Test
    void missingSegmentThrowsException() {
        TtsAudioCreationService service = new TtsAudioCreationService(provider(null), "mock");
        AudiobookProject project = new AudiobookProject(
            "test-project",
            "user-1",
            "Test Project",
            AudiobookProjectStatus.DRAFT,
            "text",
            0,
            1,
            0,
            Instant.now(),
            Instant.now()
        );

        assertThrows(IndexOutOfBoundsException.class, () -> service.createAudio(project, 0));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<GoogleTtsClient> provider(GoogleTtsClient googleTtsClient) {
        ObjectProvider<GoogleTtsClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(googleTtsClient);
        return provider;
    }

    private AudiobookProject mockProjectWithSegment(String text) {
        return mockProjectWithSegments(text);
    }

    private AudiobookProject mockProjectWithSegments(String... texts) {
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            "test-project",
            "user-1",
            "Test Project",
            AudiobookProjectStatus.DRAFT,
            "text",
            texts.length,
            1,
            60,
            now,
            now
        );
        for (int i = 0; i < texts.length; i++) {
            SpeakerCharacter character = new SpeakerCharacter(
                "char-" + i,
                "test-project",
                i,
                "Narrator",
                "Main narrator",
                SpeakerVoice.KORE,
                now
            );
            AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
                "segment-" + i,
                project,
                i,
                "Segment " + (i + 1),
                AudiobookSpeechSegmentReviewStatus.PENDING,
                30,
                now,
                now,
                texts[i],
                texts[i],
                character
            );
            project.getSpeechSegments().add(segment);
        }
        return project;
    }
}

